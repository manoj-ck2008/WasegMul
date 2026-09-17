package com.agrelius.wasegmul

import kotlin.experimental.and

/**
 * Tiny dependency-free codec for persisting the top-K subclass predictions as a string
 * in Room (which cannot store structured collections directly).
 *
 * Format:  "Plastic|0.92;Paper|0.04;Glass|0.02"
 * Labels are percent-encoded using UTF-8 byte-level encoding to safely handle any
 * characters including `|`, `;`, and non-ASCII (e.g. accented letters, CJK).
 * Empty / unparseable input decodes to an empty list.
 */
object PredictionCodec {

    private const val HEX_CHARS = "0123456789ABCDEF"

    fun encode(predictions: List<Pair<String, Float>>): String =
        predictions
            .filter { (label, conf) -> label.isNotBlank() && conf.isFinite() }
            .joinToString(";") { (label, conf) ->
                percentEncodeUtf8(label) + "|" + conf
            }

    fun decode(raw: String?): List<Pair<String, Float>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val pipeIdx = entry.lastIndexOf('|')
            if (pipeIdx <= 0) return@mapNotNull null
            val label = percentDecodeUtf8(entry.substring(0, pipeIdx)).trim()
            if (label.isEmpty()) return@mapNotNull null
            val conf = entry.substring(pipeIdx + 1).trim().toFloatOrNull() ?: return@mapNotNull null
            if (!conf.isFinite()) return@mapNotNull null
            label to conf
        }
    }

    private fun percentEncodeUtf8(s: String): String {
        val utf8Bytes = s.encodeToByteArray()
        return buildString {
            for (b in utf8Bytes) {
                val unsigned = b.toInt() and 0xFF
                // ASCII-only unreserved check: multibyte UTF-8 bytes (0x80+) MUST be encoded.
                val isUnreservedAscii = (unsigned in 48..57) || // 0-9
                    (unsigned in 65..90) || // A-Z
                    (unsigned in 97..122) || // a-z
                    unsigned == '-'.code || unsigned == '_'.code ||
                    unsigned == '.'.code || unsigned == '~'.code
                if (isUnreservedAscii) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(HEX_CHARS[(unsigned shr 4) and 0x0F])
                    append(HEX_CHARS[unsigned and 0x0F])
                }
            }
        }
    }

    private fun percentDecodeUtf8(s: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            when {
                s[i] == '%' && i + 2 < s.length -> {
                    val hex = s.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        bytes.add(code.toByte())
                        i += 3
                    } else {
                        bytes.add(s[i].code.toByte())
                        i++
                    }
                }
                // NOTE: '+' is NOT decoded to space. Encode emits %2B for '+',
                // so a raw '+' is a literal plus (e.g. legacy data). Decoding it
                // to space would corrupt labels.
                else -> {
                    bytes.add(s[i].code.toByte())
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
