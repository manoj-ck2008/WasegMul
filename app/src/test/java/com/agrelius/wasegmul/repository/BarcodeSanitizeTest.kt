package com.agrelius.wasegmul.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the Tier-1/3 junk gate (CONTEXT.md Barcode Resolution): pseudo-codes,
 * blanks, digit-less payloads and non-GTIN URLs must resolve to null and never
 * reach Room or the OFF network cascade.
 */
class BarcodeSanitizeTest {

    @Test
    fun sanitizeBarcode_rejectsPseudoCodeKeys() {
        assertNull(BarcodeRepository.sanitizeBarcode("code_1726598400000"))
        assertNull(BarcodeRepository.sanitizeBarcode("code_abc"))
    }

    @Test
    fun sanitizeBarcode_rejectsBlankAndDigitless() {
        assertNull(BarcodeRepository.sanitizeBarcode(""))
        assertNull(BarcodeRepository.sanitizeBarcode("   "))
        assertNull(BarcodeRepository.sanitizeBarcode("no digits here!"))
    }

    @Test
    fun sanitizeBarcode_rejectsNonGtinUrl() {
        assertNull(BarcodeRepository.sanitizeBarcode("https://example.com/no-gtin-here"))
    }

    @Test
    fun sanitizeBarcode_extractsGtinFromGs1Url() {
        assertEquals(
            "08435111111112",
            BarcodeRepository.sanitizeBarcode("https://id.gs1.org/01/08435111111112")
        )
    }

    @Test
    fun sanitizeBarcode_passesThroughValidGtin() {
        assertEquals("4006381333931", BarcodeRepository.sanitizeBarcode("4006381333931"))
    }

    @Test
    fun sanitizeBarcode_zeroPadsUpcA() {
        assertEquals("0123456789012", BarcodeRepository.sanitizeBarcode("123456789012"))
    }

    @Test
    fun sanitizeBarcode_stripsSpacesAndDashes() {
        assertEquals("4006381333931", BarcodeRepository.sanitizeBarcode("40063813-33931"))
    }
}
