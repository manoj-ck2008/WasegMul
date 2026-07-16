package com.agrelius.wasegmul

/**
 * Tiny dependency-free codec for persisting the top-K subclass predictions as a string
 * in Room (which cannot store structured collections directly).
 *
 * Format:  "Plastic|0.92;Paper|0.04;Glass|0.02"
 * Empty / unparseable input decodes to an empty list.
 */
object PredictionCodec {

    fun encode(predictions: List<Pair<String, Float>>): String =
        predictions.joinToString(";") { (label, conf) -> label + "|" + conf }

    fun decode(raw: String?): List<Pair<String, Float>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size != 2) return@mapNotNull null
            val conf = parts[1].toFloatOrNull() ?: return@mapNotNull null
            parts[0] to conf
        }
    }
}
