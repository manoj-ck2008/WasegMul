package com.agrelius.wasegmul

import org.junit.Assert.*
import org.junit.Test

class PredictionCodecTest {

    @Test
    fun testEncodeDecode_roundtrip() {
        val original = listOf(
            "Plastic" to 0.92f,
            "Glass" to 0.05f,
            "Paper" to 0.03f
        )

        val encoded = PredictionCodec.encode(original)
        assertTrue(encoded.contains("Plastic"))
        assertTrue(encoded.contains("0.92"))

        val decoded = PredictionCodec.decode(encoded)
        assertEquals(3, decoded.size)
        assertEquals("Plastic", decoded[0].first)
        assertEquals(0.92f, decoded[0].second, 0.001f)
        assertEquals("Glass", decoded[1].first)
        assertEquals(0.05f, decoded[1].second, 0.001f)
        assertEquals("Paper", decoded[2].first)
        assertEquals(0.03f, decoded[2].second, 0.001f)
    }

    @Test
    fun testSpecialCharacters_inLabels() {
        val original = listOf(
            "Air-Conditioner; special|test" to 0.75f,
            "automobile wastes & tires" to 0.25f
        )

        val encoded = PredictionCodec.encode(original)
        val decoded = PredictionCodec.decode(encoded)

        assertEquals(2, decoded.size)
        assertEquals("Air-Conditioner; special|test", decoded[0].first)
        assertEquals(0.75f, decoded[0].second, 0.001f)
        assertEquals("automobile wastes & tires", decoded[1].first)
        assertEquals(0.25f, decoded[1].second, 0.001f)
    }

    @Test
    fun testEmptyAndMalformed_returnsEmptyList() {
        assertTrue(PredictionCodec.decode(null).isEmpty())
        assertTrue(PredictionCodec.decode("").isEmpty())
        assertTrue(PredictionCodec.decode("   ").isEmpty())
        assertTrue(PredictionCodec.decode("invalid_data_without_pipe").isEmpty())
    }

    @Test
    fun testNonAsciiCharactersAndNaNFiltering() {
        val original = listOf(
            "Café & Résidu" to 0.85f,
            "Battery🔋" to 0.15f,
            "InvalidNaN" to Float.NaN,
            "InvalidInf" to Float.POSITIVE_INFINITY
        )

        val encoded = PredictionCodec.encode(original)
        val decoded = PredictionCodec.decode(encoded)

        assertEquals(2, decoded.size)
        assertEquals("Café & Résidu", decoded[0].first)
        assertEquals(0.85f, decoded[0].second, 0.001f)
        assertEquals("Battery🔋", decoded[1].first)
        assertEquals(0.15f, decoded[1].second, 0.001f)
    }
}
