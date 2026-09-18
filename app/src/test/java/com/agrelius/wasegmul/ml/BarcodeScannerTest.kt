package com.agrelius.wasegmul.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeScannerTest {

    @Test
    fun isValidEan13_withValidCodes_returnsTrue() {
        val scanner = BarcodeScanner()

        // Known valid standard EAN-13 codes
        val validCodes = listOf(
            "4006381333931", // Stabilo Boss
            "9780201379624", // Book ISBN-13
            "6291041500213", // GS1 official sample
            "0000000000000", // All zeroes
            "0000000000017"  // Single 1 with check digit 7
        )

        for (code in validCodes) {
            assertTrue("Expected valid EAN-13 for code $code", scanner.isValidEan13(code))
            assertTrue("Expected valid EAN-13 for code $code via companion", BarcodeScanner.isValidEan13(code))
        }

        scanner.close()
    }

    @Test
    fun isValidEan13_withIncorrectCheckDigit_returnsFalse() {
        val scanner = BarcodeScanner()

        val invalidCheckDigitCodes = listOf(
            "4006381333932", // Corrupted check digit (should be 1)
            "9780201379620", // Corrupted check digit (should be 4)
            "6291041500219"  // Corrupted check digit (should be 3)
        )

        for (code in invalidCheckDigitCodes) {
            assertFalse("Expected invalid EAN-13 for code $code", scanner.isValidEan13(code))
            assertFalse("Expected invalid EAN-13 for code $code via companion", BarcodeScanner.isValidEan13(code))
        }

        scanner.close()
    }

    @Test
    fun isValidEan13_withInvalidFormatOrLength_returnsFalse() {
        val scanner = BarcodeScanner()

        val malformedCodes = listOf(
            "",
            "123",
            "400638133393",    // 12 digits (UPC-A length)
            "40063813339311",  // 14 digits
            "400638133393X",   // Non-digit character
            "4006 38133393",   // Space included
            "-400638133393",   // Negative sign
            "abcdefghijklm"    // Alpha characters
        )

        for (code in malformedCodes) {
            assertFalse("Expected invalid EAN-13 for malformed code '$code'", scanner.isValidEan13(code))
        }

        scanner.close()
    }

    @Test
    fun barcodeResult_propertiesAndEquality() {
        val result1 = BarcodeResult(
            rawValue = "4006381333931",
            format = 32,
            displayValue = "4006381333931"
        )
        val result2 = BarcodeResult(
            rawValue = "4006381333931",
            format = 32,
            displayValue = "4006381333931"
        )

        assertEquals("4006381333931", result1.rawValue)
        assertEquals(32, result1.format)
        assertEquals("4006381333931", result1.displayValue)
        assertEquals(result1, result2)
    }

    @Test
    fun extractGtinFromUrl_extractsGtinCorrectly() {
        assertEquals("08435111111112", BarcodeScanner.extractGtinFromUrl("https://id.gs1.org/01/08435111111112"))
        assertEquals("04006381333931", BarcodeScanner.extractGtinFromUrl("https://example.com/gtin/04006381333931/lot/99"))
        assertEquals("1234567890", BarcodeScanner.extractGtinFromUrl("1234567890"))
    }

    @Test
    fun scannerLifecycle_closeCanBeInvokedSafely() {
        val scanner = BarcodeScanner()
        scanner.resetCooldown()
        scanner.close()
        // Multiple close calls must be safe and idempotent
        scanner.close()
    }
}
