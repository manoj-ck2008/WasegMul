package com.agrelius.wasegmul.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single category-to-color map for the whole app (one source of truth).
 *
 * Every screen (History donut/badges, Barcode sheet, Guide cards, YOLO overlay,
 * result headers) must resolve Category colors through [categoryColor] so color
 * semantics stay consistent. Hues are chosen to stay distinguishable in both
 * light and dark themes; color is never the sole encoding — call sites must
 * also render the Category name as text.
 */
@Composable
fun categoryColor(category: String): Color {
    return when {
        category.equals("E-Waste", ignoreCase = true) -> ErrorRed
        category.equals("Organic", ignoreCase = true) -> HighConfidenceGreen
        category.equals("Recyclable", ignoreCase = true) -> SkyBlueDeep
        // Hazardous uses amber (distinct from E-Waste red and Organic green).
        category.equals("Hazardous", ignoreCase = true) -> Color(0xFFE67E22)
        category.equals("Residual", ignoreCase = true) ->
            MaterialTheme.colorScheme.onSurfaceVariant
        // "Trash" is the legacy runtime name for Residual; map identically until
        // the taxonomy unification batch renames it.
        category.equals("Trash", ignoreCase = true) ->
            MaterialTheme.colorScheme.onSurfaceVariant
        category.equals("Uncertain", ignoreCase = true) -> MediumConfidenceYellow
        category.equals("Unknown", ignoreCase = true) -> MediumConfidenceYellow
        else -> MediumConfidenceYellow
    }
}
