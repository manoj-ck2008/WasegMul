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

    fun encode(predictions: List<Pair<String, Float>>): String =
        predictions.joinToString(";") { (label, conf) ->
            percentEncodeUtf8(label) + "|" + conf
        }

    fun decode(raw: String?): List<Pair<String, Float>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val pipeIdx = entry.lastIndexOf('|')
            if (pipeIdx < 0) return@mapNotNull null
            val label = percentDecodeUtf8(entry.substring(0, pipeIdx))
            val conf = entry.substring(pipeIdx + 1).toFloatOrNull() ?: return@mapNotNull null
            label to conf
        }
    }

    private fun percentEncodeUtf8(s: String): String {
        val utf8Bytes = s.toByteArray(Charsets.UTF_8)
        return buildString {
            for (b in utf8Bytes) {
                val unsigned = b.toInt() and 0xFF
                val c = unsigned.toChar()
                if (c.isLetterOrDigit() || c in "-_.~") {
                    append(c)
                } else {
                    append('%')
                    append("%02X".format(unsigned))
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
                s[i] == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                else -> {
                    bytes.add(s[i].code.toByte())
                    i++
                }
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
