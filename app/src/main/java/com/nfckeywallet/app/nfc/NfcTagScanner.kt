package com.nfckeywallet.app.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import com.nfckeywallet.app.model.EmulationMode

data class ScannedTagResult(
    val uidHex: String,
    val atqaHex: String,
    val sakHex: String,
    val techList: List<String>,
    val ndefRawHex: String,
    val ndefDisplayText: String,
    val detectedAidHex: String,
    val suggestedMode: EmulationMode,
    val isHardwareUidOnlyWarning: Boolean
)

object NfcTagScanner {

    fun parseTag(tag: Tag): ScannedTagResult {
        val uidHex = HexUtils.bytesToHex(tag.id ?: ByteArray(0), ":")
        val techList = tag.techList.map { it.substringAfterLast('.') }

        var atqaHex = "0400"
        var sakHex = "28"
        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            runCatching {
                val atqa = nfcA.atqa
                if (atqa != null && atqa.size == 2) {
                    atqaHex = HexUtils.bytesToHex(atqa).uppercase()
                }
                val rawSak = nfcA.sak.toInt() and 0xFF
                // On NXP/Android HCE controllers, pure SAK 0x08 (MIFARE 1K) is often silent;
                // auto-upgrade to 0x28 (MIFARE+HCE) so intercoms respond out of the box.
                sakHex = if (rawSak == 0x08) "28" else String.format("%02X", rawSak)
            }
        }

        var ndefRawHex = ""
        var ndefDisplayText = ""

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
                if (msg != null) {
                    ndefRawHex = HexUtils.bytesToHex(msg.toByteArray())
                    ndefDisplayText = NdefBuilder.extractDisplayText(msg)
                }
            } catch (_: Exception) {
                val cached = ndef.cachedNdefMessage
                if (cached != null) {
                    ndefRawHex = HexUtils.bytesToHex(cached.toByteArray())
                    ndefDisplayText = NdefBuilder.extractDisplayText(cached)
                }
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        val hasIsoDep = IsoDep.get(tag) != null
        val isMifare = MifareClassic.get(tag) != null || MifareUltralight.get(tag) != null

        val suggestedMode = if (ndefRawHex.isNotEmpty()) {
            EmulationMode.NDEF_TYPE4
        } else if (hasIsoDep) {
            EmulationMode.CUSTOM_AID
        } else {
            EmulationMode.NDEF_TYPE4
        }

        val isHardwareUidOnly = ndefRawHex.isEmpty() && !hasIsoDep && (isMifare || techList.isNotEmpty())

        return ScannedTagResult(
            uidHex = uidHex,
            atqaHex = atqaHex,
            sakHex = sakHex,
            techList = techList,
            ndefRawHex = ndefRawHex,
            ndefDisplayText = ndefDisplayText,
            detectedAidHex = if (ndefRawHex.isNotEmpty()) "D2760000850101" else "F0010203040506",
            suggestedMode = suggestedMode,
            isHardwareUidOnlyWarning = isHardwareUidOnly
        )
    }
}
