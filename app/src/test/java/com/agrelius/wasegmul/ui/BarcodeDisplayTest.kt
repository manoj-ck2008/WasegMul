package com.agrelius.wasegmul.ui

import com.agrelius.wasegmul.ui.history.CsvRow
import com.agrelius.wasegmul.ui.history.writeHistoryCsv
import com.agrelius.wasegmul.ui.result.humanizeLabel
import com.agrelius.wasegmul.ui.result.isCategoryName
import com.agrelius.wasegmul.ui.result.parseBarcodeDisplay
import com.agrelius.wasegmul.ui.result.parseLegacyVector
import com.agrelius.wasegmul.ui.result.sanitizeCsvCell
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

/**
 * Vertical-slice tests for the UI batch's pure seams: the single
 * barcode-display parser (pipe-hardening), correction-taxonomy guard,
 * label humanization, CSV sanitization and the streaming exporter.
 */
class BarcodeDisplayTest {

    @Test
    fun `first-class columns win over legacy vector`() {
        val display = parseBarcodeDisplay(
            source = "barcode",
            barcode = "8901234567890",
            productName = "Oats",
            featureVector = "barcode:stale|Stale Name|Brand"
        )
        assertNotNull(display)
        assertEquals("8901234567890", display!!.code)
        assertEquals("Oats", display.productName)
    }

    @Test
    fun `pipe inside product name survives legacy parse`() {
        // code|name-with-pipe|brand: first segment is the code, the last is
        // the brand, the middle joins back into the name.
        val display = parseLegacyVector("barcode:123|A|B|Brand")
        assertNotNull(display)
        assertEquals("123", display!!.code)
        assertEquals("A|B", display.productName)
    }

    @Test
    fun `two-segment legacy vector parses`() {
        val display = parseLegacyVector("barcode:456|Milk")
        assertEquals("456", display!!.code)
        assertEquals("Milk", display.productName)
    }

    @Test
    fun `non-barcode vectors return null`() {
        assertNull(parseLegacyVector("feature:0.1,0.2"))
        assertNull(parseLegacyVector(null))
        assertNull(
            parseBarcodeDisplay(
                source = "camera",
                barcode = null,
                productName = null,
                featureVector = "feature:0.1"
            )
        )
    }

    @Test
    fun `categories are rejected as corrections`() {
        assertTrue(isCategoryName("E-Waste"))
        assertTrue(isCategoryName("organic"))
        assertTrue(isCategoryName(" Trash "))
        assertTrue(isCategoryName("Uncertain"))
        assertFalse(isCategoryName("Plastic"))
        assertFalse(isCategoryName("Cardboard"))
        assertFalse(isCategoryName("Miscellaneous Trash"))
    }

    @Test
    fun `labels are humanized locale-independently`() {
        assertEquals("Plastic Bottle", humanizeLabel("plastic_bottle"))
        assertEquals("Milk", humanizeLabel("milk"))
        assertEquals("Cell Phone", humanizeLabel("cell phone"))
    }

    @Test
    fun `csv cells guard formula injection`() {
        assertEquals("\"'=cmd|' /C calc'!A0\"", sanitizeCsvCell("=cmd|' /C calc'!A0"))
        assertEquals("\"'+100\"", sanitizeCsvCell("+100"))
        assertEquals("\"plain\"", sanitizeCsvCell("plain"))
        assertEquals("\"say \"\"hi\"\"\"", sanitizeCsvCell("say \"hi\""))
    }

    @Test
    fun `csv export streams header plus sanitized rows`() {
        val writer = StringWriter()
        writer.buffered().use { out ->
            writeHistoryCsv(
                out,
                sequenceOf(
                    CsvRow(
                        id = 1L,
                        timestampIso = "2026-09-18T00:00:00Z",
                        timestampEpoch = 1L,
                        category = "Recyclable",
                        subclass = "Plastic",
                        confidence = 0.94567f,
                        weightKg = 0.05,
                        feedback = "correct",
                        correctedSubclass = null,
                        source = "camera",
                        productName = "=evil",
                        barcode = null,
                        imagePath = null
                    )
                )
            )
        }
        val lines = writer.toString().trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("ID,TimestampISO"))
        assertTrue(lines[0].contains("Source,ProductName,Barcode,ImagePath"))
        // ROOT decimals + guarded product name.
        assertTrue(lines[1].contains("0.9457"))
        assertTrue(lines[1].contains("\"'=evil\""))
    }
}
