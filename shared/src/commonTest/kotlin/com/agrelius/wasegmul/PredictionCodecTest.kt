package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.46 fixes.
 */
class PredictionCodecTest {

    @Test
    fun roundtrip_basic() {
        val original = listOf("Plastic" to 0.92f, "Glass" to 0.05f)
        val decoded = PredictionCodec.decode(PredictionCodec.encode(original))
        assertEquals(2, decoded.size)
        assertEquals("Plastic", decoded[0].first)
        assertEquals(0.92f, decoded[0].second, 0.001f)
    }

    @Test
    fun decode_malformedPercentBytes_neverThrows() {
        // Lone %FF%FE (invalid UTF-8) previously threw on the Room read path.
        val decoded = PredictionCodec.decode("Plastic|0.9;%FF%FE|0.1")
        assertEquals(2, decoded.size)
        assertEquals("Plastic", decoded[0].first)
        assertTrue(decoded[1].first.isNotEmpty(), "malformed entry substitutes U+FFFD, not dropped/crash")
        assertTrue(!decoded[1].first.contains("Plastic"))
    }

    @Test
    fun decode_outOfRangeConfidences_dropped() {
        assertTrue(PredictionCodec.decode("Plastic|5.0").isEmpty())
        assertTrue(PredictionCodec.decode("Plastic|-1.0").isEmpty())
        assertTrue(PredictionCodec.decode("Plastic|NaN").isEmpty())
        // In-range neighbour still decodes.
        val mixed = PredictionCodec.decode("Plastic|5.0;Glass|0.2")
        assertEquals(listOf("Glass" to 0.2f), mixed)
    }

    @Test
    fun decode_whitespaceContract_trimsEdgesKeepsInside() {
        val decoded = PredictionCodec.decode("  automobile wastes  |0.25")
        assertEquals(listOf("automobile wastes" to 0.25f), decoded)
    }

    @Test
    fun decode_legacyRawUnicode_notTruncated() {
        // Pre-codec rows stored raw non-ASCII (old bug truncated via toByte()).
        val decoded = PredictionCodec.decode("Café & Résidu|0.85")
        assertEquals(1, decoded.size)
        assertEquals("Café & Résidu", decoded[0].first)
    }

    @Test
    fun caps_boundHostileInput() {
        val huge = (1..60).joinToString(";") { "label$it|0.5" }
        assertEquals(PredictionCodec.MAX_ENTRIES, PredictionCodec.decode(huge).size)
        assertTrue(PredictionCodec.decode("x".repeat(PredictionCodec.MAX_RAW_CHARS + 1)).isEmpty())
        assertTrue(PredictionCodec.decode("${"y".repeat(PredictionCodec.MAX_LABEL_CHARS + 1)}|0.5").isEmpty())
    }

    @Test
    fun encode_dropsNonFinite() {
        val encoded = PredictionCodec.encode(listOf("A" to Float.NaN, "B" to 0.5f))
        assertEquals("B|0.5", encoded)
    }
}
