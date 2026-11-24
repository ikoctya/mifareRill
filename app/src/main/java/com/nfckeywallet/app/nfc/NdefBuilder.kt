package com.nfckeywallet.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.StandardCharsets

object HexUtils {
    fun bytesToHex(bytes: ByteArray, separator: String = ""): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * (2 + separator.length))
        for (i in bytes.indices) {
            if (i > 0 && separator.isNotEmpty()) sb.append(separator)
            val v = bytes[i].toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(Regex("[^0-9A-Fa-f]"), "")
        if (clean.length < 2) return ByteArray(0)
        val evenLen = clean.length - (clean.length % 2)
        val out = ByteArray(evenLen / 2)
        var i = 0
        while (i < evenLen) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    fun reverseOctets(hex: String, separator: String = ":"): String {
        val bytes = hexToBytes(hex)
        if (bytes.isEmpty()) return hex
        return bytesToHex(bytes.reversedArray(), separator)
    }

    private const val HEX_CHARS = "0123456789ABCDEF"
}

object NdefBuilder {
    fun buildTextMessageBytes(text: String): ByteArray {
        val record = NdefRecord.createTextRecord("ru", text)
        return NdefMessage(arrayOf(record)).toByteArray()
    }

    fun buildUriMessageBytes(uri: String): ByteArray {
        val record = NdefRecord.createUri(uri)
        return NdefMessage(arrayOf(record)).toByteArray()
    }

    fun extractDisplayText(message: NdefMessage?): String {
        if (message == null || message.records.isEmpty()) return ""
        return message.records.mapNotNull { record ->
            try {
                when {
                    record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        val payload = record.payload
                        if (payload.isEmpty()) return@mapNotNull null
                        val isUtf16 = (payload[0].toInt() and 0x80) != 0
                        val langLen = payload[0].toInt() and 0x3F
                        val charset = if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
                        if (payload.size > 1 + langLen) {
                            String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
                        } else null
                    }
                    record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(NdefRecord.RTD_URI) -> {
                        record.toUri()?.toString()
                    }
                    record.tnf == NdefRecord.TNF_ABSOLUTE_URI -> {
                        String(record.payload, StandardCharsets.UTF_8)
                    }
                    else -> {
                        val raw = String(record.payload, StandardCharsets.UTF_8)
                        if (raw.all { it.code in 32..126 || it.isLetterOrDigit() || it.isWhitespace() }) {
                            raw
                        } else {
                            "HEX: ${HexUtils.bytesToHex(record.payload, " ")}"
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }.joinToString(" | ")
    }
}
