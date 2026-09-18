package com.agrelius.wasegmul

/**
 * Tiny dependency-free codec for persisting the top-K subclass predictions as a string
 * in Room (which cannot store structured collections directly).
 *
 * Format:  "Plastic|0.92;Paper|0.04;Glass|0.02"
 * Labels are percent-encoded using UTF-8 byte-level encoding to safely handle any
 * characters including `|`, `;`, and non-ASCII (e.g. accented letters, CJK).
 * Empty / unparseable input decodes to an empty list.
 *
 * ## Charset
 * Encoding reads UTF-8 bytes via [String.encodeToByteArray] and decoding rebuilds them
 * with an explicit UTF-8 pass using U+FFFD substitution. Malformed byte sequences (e.g. a
 * lone `%FF%FE` from corrupt storage) NEVER throw: [decode] substitutes U+FFFD per
 * maximal-subpart, so a single bad entry cannot crash a Room read path.
 *
 * ## Whitespace contract
 * Spaces inside labels are stored literally (NOT `%20`); leading/trailing whitespace of
 * the whole label is trimmed on decode. Consequence: a label with significant leading or
 * trailing spaces does not round-trip exactly — documented, accepted (model labels never
 * carry edge spaces). `'+'` is a literal plus, never a space (encode emits `%2B`).
 *
 * ## Range contract
 * Confidences must be calibrated probabilities in `0..1`. [encode] drops non-finite
 * entries; [decode] drops non-finite AND out-of-range entries (legacy rows containing
 * `5.0`/`-1.0` decode to fewer entries rather than violating [requireUnitConfidence]
 * downstream).
 *
 * ## Size caps (DoS guard for hostile/long DB strings)
 * [MAX_RAW_CHARS] bounds the input; [MAX_ENTRIES] bounds decoded entries;
 * [MAX_LABEL_CHARS] bounds a single label. Excess is dropped, never throws.
 */
object PredictionCodec {

    private const val HEX_CHARS = "0123456789ABCDEF"

    /** Maximum raw string length accepted by [decode]; longer input decodes to empty. */
    const val MAX_RAW_CHARS = 8_192

    /** Maximum entries decoded from one string (also the encode cap). */
    const val MAX_ENTRIES = 50

    /** Maximum characters per decoded label; longer labels are dropped. */
    const val MAX_LABEL_CHARS = 128

    fun encode(predictions: List<Pair<String, Float>>): String =
        predictions
            .asSequence()
            .filter { (label, conf) -> label.isNotBlank() && conf.isFinite() }
            .take(MAX_ENTRIES)
            .joinToString(";") { (label, conf) ->
                percentEncodeUtf8(label) + "|" + conf
            }

    /**
     * Exception-safe: never throws on hostile input (overlong, malformed escapes, bad
     * UTF-8, out-of-range confidences). Unparseable entries are skipped.
     */
    fun decode(raw: String?): List<Pair<String, Float>> {
        if (raw.isNullOrBlank()) return emptyList()
        if (raw.length > MAX_RAW_CHARS) return emptyList()
        return raw.split(';').asSequence().mapNotNull { entry ->
            val pipeIdx = entry.lastIndexOf('|')
            if (pipeIdx <= 0) return@mapNotNull null
            val label = percentDecodeUtf8Lossy(entry.substring(0, pipeIdx)).trim()
            if (label.isEmpty() || label.length > MAX_LABEL_CHARS) return@mapNotNull null
            val conf = entry.substring(pipeIdx + 1).trim().toFloatOrNull() ?: return@mapNotNull null
            if (!conf.isFinite() || conf !in 0f..1f) return@mapNotNull null
            label to conf
        }.take(MAX_ENTRIES).toList()
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

    /**
     * Percent-decodes to UTF-8 bytes, then decodes with U+FFFD substitution for malformed
     * sequences (maximal-subpart). Never throws.
     */
    private fun percentDecodeUtf8Lossy(s: String): String {
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
                        // Literal '%': encode as its own UTF-8 bytes (never truncate chars).
                        bytes.addAll(s[i].toString().encodeToByteArray().toList())
                        i++
                    }
                }
                // NOTE: '+' is NOT decoded to space. Encode emits %2B for '+',
                // so a raw '+' is a literal plus (e.g. legacy data). Decoding it
                // to space would corrupt labels.
                else -> {
                    bytes.addAll(s[i].toString().encodeToByteArray().toList())
                    i++
                }
            }
        }
        // Single manual UTF-8 pass with U+FFFD substitution: valid sequences decode
        // identically to decodeToString(); malformed ones substitute instead of throwing.
        return repairMalformedUtf8(bytes.toByteArray())
    }

    /**
     * Decodes UTF-8 with U+FFFD substitution for malformed subsequences.
     * Implemented manually so a corrupt stored row can never throw on a Room read path.
     */
    private fun repairMalformedUtf8(bytes: ByteArray): String {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            fun continuationAt(j: Int): Boolean =
                j < bytes.size && (bytes[j].toInt() and 0xC0) == 0x80
            when {
                b0 < 0x80 -> { out.append(b0.toChar()); i += 1 }
                b0 in 0xC2..0xDF && continuationAt(i + 1) -> {
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    out.append(((b0 and 0x1F) shl 6 or (b1 and 0x3F)).toChar()); i += 2
                }
                b0 in 0xE0..0xEF && continuationAt(i + 1) && continuationAt(i + 2) -> {
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    out.append(((b0 and 0x0F) shl 12 or ((b1 and 0x3F) shl 6) or (b2 and 0x3F)).toChar())
                    i += 3
                }
                b0 in 0xF0..0xF4 && continuationAt(i + 1) && continuationAt(i + 2) && continuationAt(i + 3) -> {
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    var cp = ((b0 and 0x07) shl 18) or ((b1 and 0x3F) shl 12) or
                        ((b2 and 0x3F) shl 6) or (b3 and 0x3F)
                    cp -= 0x10000
                    out.append(((cp shr 10) + 0xD800).toChar())
                    out.append(((cp and 0x3FF) + 0xDC00).toChar())
                    i += 4
                }
                else -> { out.append('�'); i += 1 }
            }
        }
        return out.toString()
    }
}
