package com.agrelius.wasegmul

/**
 * Arbitrates between Category and Subclass models to resolve clashes.
 * Pure Kotlin logic for multiplatform support.
 */
object MLArbitrator {
    private const val SUBCLASS_CONFIDENCE_THRESHOLD = 0.65f
    private const val CATEGORY_CONFIDENCE_THRESHOLD = 0.50f

    fun arbitrate(prediction: PredictionResult): PredictionResult {
        val rawCategory = prediction.category
        val rawSubclass = prediction.subcategory
        val subConf = prediction.subcategoryConfidence
        val catConf = prediction.categoryConfidence

        val mappedCategory = WasteMapping.getCategory(rawSubclass)

        return when {
            // High confidence in subclass - trust mapping over category model
            subConf > SUBCLASS_CONFIDENCE_THRESHOLD -> {
                prediction.copy(category = mappedCategory)
            }

            // Low confidence in both - tag as Uncertain
            subConf < CATEGORY_CONFIDENCE_THRESHOLD && catConf < CATEGORY_CONFIDENCE_THRESHOLD -> {
                prediction.copy(category = "Uncertain")
            }

            // If raw category model is weak and disagrees with mapping, trust mapping
            rawCategory != mappedCategory && catConf < 0.7f -> {
                prediction.copy(category = mappedCategory)
            }

            else -> prediction
        }
    }
}
