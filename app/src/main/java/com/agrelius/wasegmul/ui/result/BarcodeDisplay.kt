package com.agrelius.wasegmul.ui.result

import java.util.Locale

/**
 * Single Barcode-display parser for the app.
 *
 * Provenance precedence: first-class columns (`source` / `barcode` /
 * `productName`) win; the legacy `featureVector` pipe-tag
 * (`"barcode:code|name|brand"`) is only a fallback. Every screen
 * (Result, Home recent-taps, History cards) must go through
 * [parseBarcodeDisplay] instead of re-splitting the vector inline, so
 * `|` inside product names and `limit=2` vs `limit=3` divergences cannot
 * recur. New code must write the first-class columns, never the vector.
 */
data class BarcodeDisplay(
    val code: String,
    val productName: String?
) {
    val isBarcode: Boolean = code.isNotBlank()
}

fun parseBarcodeDisplay(
    source: String?,
    barcode: String?,
    productName: String?,
    featureVector: String?
): BarcodeDisplay? {
    val cleanBarcode = barcode?.takeIf { it.isNotBlank() }
    val cleanName = productName?.takeIf { it.isNotBlank() }
    if (source == "barcode" || cleanBarcode != null) {
        // First-class columns present. Fall back to the legacy vector only for
        // whichever half is missing.
        val legacy = parseLegacyVector(featureVector)
        return BarcodeDisplay(
            code = cleanBarcode ?: legacy?.code.orEmpty(),
            productName = cleanName ?: legacy?.productName
        ).takeIf { it.isBarcode }
    }
    // Legacy-only rows.
    return parseLegacyVector(featureVector)
}

/**
 * Parses `"barcode:code|name|brand"`. Hardened: `|` inside product names is
 * tolerated by treating the first segment as the code, the last segment
 * (when 3+) as the brand, and everything in between as the name.
 */
fun parseLegacyVector(featureVector: String?): BarcodeDisplay? {
    if (featureVector == null || !featureVector.startsWith("barcode:")) return null
    val raw = featureVector.removePrefix("barcode:")
    if (raw.isBlank()) return null
    val parts = raw.split("|")
    val code = parts.getOrNull(0).orEmpty()
    if (code.isBlank()) return null
    val name = when {
        parts.size <= 1 -> null
        parts.size == 2 -> parts[1].takeIf { it.isNotBlank() }
        else -> parts.subList(1, parts.size - 1).joinToString("|").takeIf { it.isNotBlank() }
    }
    return BarcodeDisplay(code = code, productName = name)
}

/**
 * Canonical Category names (CONTEXT.md + legacy runtime `Trash`). A value
 * matching one of these must never be stored in `correctedSubclass`
 * (see `correction_material_options`): it corrupts the taxonomy and yields
 * `UNKNOWN` categories downstream.
 */
val CATEGORY_NAMES: Set<String> = setOf(
    "Organic", "Recyclable", "Hazardous", "E-Waste", "Residual",
    "Trash", "Unknown", "Uncertain"
)

fun isCategoryName(value: String): Boolean =
    CATEGORY_NAMES.any { it.equals(value.trim(), ignoreCase = true) }

/**
 * Humanizes raw model/KB labels for display: `plastic_bottle` -> `Plastic Bottle`.
 * Machine keys keep raw form; only UI text goes through here. Locale.ROOT:
 * title-casing must not depend on device locale (Turkish-i).
 */
fun humanizeLabel(raw: String): String {
    val cleaned = raw.replace('_', ' ').replace('-', ' ').trim()
    if (cleaned.isEmpty()) return cleaned
    return cleaned.split(Regex("\\s+")).joinToString(" ") { word ->
        word.lowercase(Locale.ROOT).replaceFirstChar { ch -> ch.uppercaseChar() }
    }
}

/**
 * Guards a CSV cell against formula injection (`=,+,-,@` prefixes) and
 * quotes it. Use for every user/model-influenced field in history export.
 */
fun sanitizeCsvCell(value: String): String {
    val needsGuard = value.startsWith("=") || value.startsWith("+") ||
        value.startsWith("-") || value.startsWith("@")
    val guarded = if (needsGuard) "'$value" else value
    return "\"${guarded.replace("\"", "\"\"")}\""
}
