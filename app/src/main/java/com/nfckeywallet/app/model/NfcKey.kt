package com.nfckeywallet.app.model

import com.nfckeywallet.app.nfc.HexUtils
import java.util.UUID

enum class EmulationMode {
    NDEF_TYPE4,
    CUSTOM_AID
}

data class NfcKey(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subtitle: String = "",
    val iconId: String = "key",
    val colorId: String = "indigo",
    val uidHex: String = "",
    val reverseOctets: Boolean = false,
    val atqaHex: String = "0400",
    val sakHex: String = "28",
    val techList: List<String> = emptyList(),
    val emulationMode: EmulationMode = EmulationMode.NDEF_TYPE4,
    val customAidHex: String = "F0010203040506",
    val ndefRawHex: String = "",
    val ndefDisplayText: String = "",
    val customResponseHex: String = "",
    val isHardwareUidOnlyWarning: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
) {
    /** Returns UID with octets reversed if [reverseOctets] is enabled. */
    fun effectiveUidHex(): String {
        if (uidHex.isBlank()) return ""
        return if (reverseOctets) {
            HexUtils.reverseOctets(uidHex, ":")
        } else {
            uidHex
        }
    }

    /** Safe ATQA (2 bytes, defaults to 0400 for MIFARE Classic 1K / 4-byte UID). */
    fun safeAtqaHex(): String {
        val clean = atqaHex.orEmpty().replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        return if (clean.length == 4) clean else "0400"
    }

    /** Safe SAK (1 byte, defaults to 28 = MIFARE+HCE for NXP/Xiaomi compatibility). */
    fun safeSakHex(): String {
        val clean = sakHex.orEmpty().replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        return if (clean.length == 2) clean else "28"
    }
}

data class AppSettings(
    val autoReleaseSeconds: Int = 30,
    val releaseAfterTap: Boolean = true,
    val autoActivateOnOpen: Boolean = true,
    val vibrateOnTap: Boolean = true,
    val enableHardwareUidEmulation: Boolean = true,
    val uidBackendPreference: String = "AUTO"
)
