package com.nfckeywallet.app.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.nfckeywallet.app.data.KeyRepository
import com.nfckeywallet.app.model.EmulationMode
import com.nfckeywallet.app.model.NfcKey

/**
 * Host-based Card Emulation (HCE) service supporting:
 * 1. NFC Forum NDEF Type 4 Tag (AID D2760000850101) — full CC + NDEF file READ BINARY state machine.
 * 2. Custom AID / ISO-DEP / PPSE APDU emulation with optional octet reversal per key.
 */
class KeyHceService : HostApduService() {

    private lateinit var repository: KeyRepository

    private var selectedFile: SelectedFile = SelectedFile.NONE
    private var activeNdefFile: ByteArray = ByteArray(0)
    private var tapNotifiedThisSession: Boolean = false

    private enum class SelectedFile {
        NONE,
        NDEF_APP,
        CC_FILE,
        NDEF_FILE,
        CUSTOM_APP
    }

    override fun onCreate() {
        super.onCreate()
        repository = KeyRepository(applicationContext)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return SW_WRONG_LENGTH
        }

        if (!repository.isEmulationEnabled()) {
            return SW_FILE_NOT_FOUND
        }

        val activeKey = repository.getSelectedKey() ?: return SW_FILE_NOT_FOUND

        val cla = commandApdu[0].toInt() and 0xFF
        val ins = commandApdu[1].toInt() and 0xFF
        val p1 = commandApdu[2].toInt() and 0xFF
        val p2 = commandApdu[3].toInt() and 0xFF

        // 1. SELECT by DF Name (AID): 00 A4 04 00 <Lc> <AID> [Le]
        if (cla == 0x00 && ins == 0xA4 && p1 == 0x04) {
            val aid = extractApduData(commandApdu)
            val aidHex = HexUtils.bytesToHex(aid).uppercase()

            // NDEF Type 4 Tag Application AID
            if (aidHex == NDEF_AID) {
                selectedFile = SelectedFile.NDEF_APP
                activeNdefFile = buildNdefFile(activeKey)
                notifyTapIfNeeded(activeKey, aidHex)
                return SW_OK
            }

            // Custom AID or Payment/PPSE fallback
            val keyCustomAid = activeKey.customAidHex.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
            if (aidHex == keyCustomAid ||
                aidHex == PPSE_AID ||
                aidHex == FALLBACK_PAYMENT_AID ||
                aidHex == DEFAULT_ACCESS_AID
            ) {
                selectedFile = SelectedFile.CUSTOM_APP
                notifyTapIfNeeded(activeKey, aidHex)

                val customResp = buildCustomResponse(activeKey)
                return if (customResp.isNotEmpty()) {
                    customResp + SW_OK
                } else {
                    SW_OK
                }
            }

            return SW_FILE_NOT_FOUND
        }

        // 2. SELECT by File ID (ISO 7816-4 Type 4 Tag): 00 A4 00 0C 02 <FID>
        if (cla == 0x00 && ins == 0xA4 && p1 == 0x00) {
            val fid = extractApduData(commandApdu)
            val fidHex = HexUtils.bytesToHex(fid).uppercase()
            return when (fidHex) {
                "E103" -> {
                    selectedFile = SelectedFile.CC_FILE
                    SW_OK
                }
                "E104" -> {
                    selectedFile = SelectedFile.NDEF_FILE
                    if (activeNdefFile.isEmpty()) {
                        activeNdefFile = buildNdefFile(activeKey)
                    }
                    SW_OK
                }
                else -> SW_FILE_NOT_FOUND
            }
        }

        // 3. READ BINARY: 00 B0 <P1=offsetHi> <P2=offsetLo> <Le>
        if (cla == 0x00 && ins == 0xB0) {
            val offset = (p1 shl 8) or p2
            val requestedLen = if (commandApdu.size >= 5) {
                val le = commandApdu[4].toInt() and 0xFF
                if (le == 0) 256 else le
            } else {
                256
            }

            val sourceBytes = when (selectedFile) {
                SelectedFile.CC_FILE -> CC_FILE_BYTES
                SelectedFile.NDEF_FILE -> activeNdefFile
                else -> return SW_CONDITIONS_NOT_SATISFIED
            }

            if (offset >= sourceBytes.size) {
                return SW_WRONG_P1_P2
            }

            val end = minOf(offset + requestedLen, sourceBytes.size)
            val slice = sourceBytes.copyOfRange(offset, end)
            return slice + SW_OK
        }

        // 4. Custom APDU fallback for non-NDEF readers
        if (selectedFile == SelectedFile.CUSTOM_APP) {
            val customResp = buildCustomResponse(activeKey)
            return if (customResp.isNotEmpty()) {
                customResp + SW_OK
            } else {
                SW_OK
            }
        }

        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = SelectedFile.NONE
        tapNotifiedThisSession = false
    }

    private fun extractApduData(apdu: ByteArray): ByteArray {
        if (apdu.size <= 5) return ByteArray(0)
        val lc = apdu[4].toInt() and 0xFF
        val available = apdu.size - 5
        val len = minOf(lc, available)
        return apdu.copyOfRange(5, 5 + len)
    }

    private fun buildNdefFile(key: NfcKey): ByteArray {
        val effectiveUid = key.effectiveUidHex()
        val ndefMessageBytes = when {
            key.ndefRawHex.isNotBlank() -> HexUtils.hexToBytes(key.ndefRawHex)
            key.ndefDisplayText.isNotBlank() -> NdefBuilder.buildTextMessageBytes(key.ndefDisplayText)
            effectiveUid.isNotBlank() -> NdefBuilder.buildTextMessageBytes("KEY:$effectiveUid")
            else -> NdefBuilder.buildTextMessageBytes(key.name)
        }
        val nlen = ndefMessageBytes.size
        val file = ByteArray(2 + nlen)
        file[0] = ((nlen ushr 8) and 0xFF).toByte()
        file[1] = (nlen and 0xFF).toByte()
        System.arraycopy(ndefMessageBytes, 0, file, 2, nlen)
        return file
    }

    private fun buildCustomResponse(key: NfcKey): ByteArray {
        val effectiveUid = key.effectiveUidHex()
        return when {
            key.customResponseHex.isNotBlank() -> {
                val raw = HexUtils.hexToBytes(key.customResponseHex)
                if (key.reverseOctets) raw.reversedArray() else raw
            }
            key.emulationMode == EmulationMode.CUSTOM_AID && effectiveUid.isNotBlank() ->
                HexUtils.hexToBytes(effectiveUid)
            key.ndefDisplayText.isNotBlank() -> key.ndefDisplayText.toByteArray(Charsets.UTF_8)
            else -> ByteArray(0)
        }
    }

    private fun notifyTapIfNeeded(key: NfcKey, matchedAid: String) {
        if (tapNotifiedThisSession) return
        tapNotifiedThisSession = true
        repository.markKeyUsed(key.id)

        val intent = Intent(ACTION_NFC_TAP_EVENT).apply {
            setPackage(packageName)
            putExtra(EXTRA_KEY_ID, key.id)
            putExtra(EXTRA_KEY_NAME, key.name)
            putExtra(EXTRA_MATCHED_AID, matchedAid)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }

    companion object {
        const val ACTION_NFC_TAP_EVENT = "com.nfckeywallet.app.ACTION_NFC_TAP_EVENT"
        const val EXTRA_KEY_ID = "extra_key_id"
        const val EXTRA_KEY_NAME = "extra_key_name"
        const val EXTRA_MATCHED_AID = "extra_matched_aid"
        const val EXTRA_TIMESTAMP = "extra_timestamp"

        private const val NDEF_AID = "D2760000850101"
        private const val PPSE_AID = "325041592E5359532E4444463031"
        private const val FALLBACK_PAYMENT_AID = "F04E46434B455901"
        private const val DEFAULT_ACCESS_AID = "F0010203040506"

        // NFC Forum Type 4 Tag v2.0 Capability Container (15 bytes)
        private val CC_FILE_BYTES = byteArrayOf(
            0x00, 0x0F,             // CCLEN = 15 bytes
            0x20,                   // Mapping Version 2.0
            0x00, 0x3B,             // MLe = 59 bytes max R-APDU
            0x00, 0x34,             // MLc = 52 bytes max C-APDU
            0x04, 0x06,             // NDEF File Control TLV (T=04, L=06)
            0xE1.toByte(), 0x04,    // File Identifier = E104
            0x7F, 0xFF.toByte(),    // Max NDEF size = 32767 bytes
            0x00,                   // Read access: granted without security
            0xFF.toByte()           // Write access: read-only
        )

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        private val SW_WRONG_P1_P2 = byteArrayOf(0x6B, 0x00)
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
    }
}
