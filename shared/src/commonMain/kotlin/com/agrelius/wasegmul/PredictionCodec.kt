package com.agrelius.wasegmul

/**
 * Tiny dependency-free codec for persisting the top-K subclass predictions as a string
 * in Room (which cannot store structured collections directly).
 *
 * Format:  "Plastic|0.92;Paper|0.04;Glass|0.02"
 * Labels are percent-encoded to safely handle any characters including `|` and `;`.
 * Empty / unparseable input decodes to an empty list.
 */
object PredictionCodec {

    fun encode(predictions: List<Pair<String, Float>>): String =
        predictions.joinToString(";") { (label, conf) ->
            percentEncode(label) + "|" + conf
        }

    fun decode(raw: String?): List<Pair<String, Float>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val pipeIdx = entry.lastIndexOf('|')
            if (pipeIdx < 0) return@mapNotNull null
            val label = percentDecode(entry.substring(0, pipeIdx))
            val conf = entry.substring(pipeIdx + 1).toFloatOrNull() ?: return@mapNotNull null
            label to conf
        }
    }

    private fun percentEncode(s: String): String = buildString {
        for (c in s) {
            when {
                c.isLetterOrDigit() || c in "-_.~" -> append(c)
                else -> {
                    append('%')
                    append("%02X".format(c.code))
                }
            }
        }
    }

    private fun percentDecode(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            when {
                s[i] == '%' && i + 2 < s.length -> {
                    val hex = s.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        append(code.toChar())
                        i += 3
                    } else {
                        append(s[i])
                        i++
                    }
                }
                s[i] == '+' -> {
                    append(' ')
                    i++
                }
                else -> {
                    append(s[i])
                    i++
                }
            }
        }
    }
}
