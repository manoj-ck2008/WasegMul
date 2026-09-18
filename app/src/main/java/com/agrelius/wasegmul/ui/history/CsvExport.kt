package com.agrelius.wasegmul.ui.history

import com.agrelius.wasegmul.ui.result.sanitizeCsvCell
import java.io.BufferedWriter
import java.util.Locale

/**
 * Streaming CSV export for Waste Records.
 *
 * Writes row-by-row to [out] so large histories cannot OOM (no in-memory
 * whole-file string). Every user/model-influenced cell goes through
 * [sanitizeCsvCell] (formula-injection guard + quoting). Machine-format
 * numbers use Locale.ROOT; display locale must never leak into exports.
 */
fun writeHistoryCsv(
    out: BufferedWriter,
    rows: Sequence<CsvRow>
) {
    out.appendLine("ID,TimestampISO,TimestampEpoch,Category,Subclass,Confidence,WeightKg,Feedback,CorrectedSubclass,Source,ProductName,Barcode,ImagePath")
    rows.forEach { row ->
        out.appendLine(
            listOf(
                row.id.toString(),
                row.timestampIso,
                row.timestampEpoch.toString(),
                sanitizeCsvCell(row.category),
                sanitizeCsvCell(row.subclass),
                String.format(Locale.ROOT, "%.4f", row.confidence),
                String.format(Locale.ROOT, "%.4f", row.weightKg),
                sanitizeCsvCell(row.feedback.orEmpty()),
                sanitizeCsvCell(row.correctedSubclass.orEmpty()),
                sanitizeCsvCell(row.source),
                sanitizeCsvCell(row.productName.orEmpty()),
                sanitizeCsvCell(row.barcode.orEmpty()),
                sanitizeCsvCell(row.imagePath.orEmpty())
            ).joinToString(",")
        )
    }
    out.flush()
}

data class CsvRow(
    val id: Long,
    val timestampIso: String,
    val timestampEpoch: Long,
    val category: String,
    val subclass: String,
    val confidence: Float,
    val weightKg: Double,
    val feedback: String?,
    val correctedSubclass: String?,
    val source: String,
    val productName: String?,
    val barcode: String?,
    val imagePath: String?
)
