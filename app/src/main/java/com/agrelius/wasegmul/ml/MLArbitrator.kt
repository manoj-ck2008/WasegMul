package com.agrelius.wasegmul.ml

import android.util.Log
import com.agrelius.wasegmul.data.PredictionResult
import com.agrelius.wasegmul.data.WasteMapping

/**
 * Arbitrates between Category and Subclass models to resolve clashes.
 */
object MLArbitrator {
    private const val TAG = "MLArbitrator"
    private const val SUBCLASS_CONFIDENCE_THRESHOLD = 0.65f
    private const val CATEGORY_CONFIDENCE_THRESHOLD = 0.50f

    fun arbitrate(prediction: PredictionResult): PredictionResult {
        val rawCategory = prediction.category
        val rawSubclass = prediction.subcategory
        val subConf = prediction.subcategoryConfidence
        val catConf = prediction.categoryConfidence

        // Step 1: Get the "Correct" category according to our hardcoded mapping
        val mappedCategory = WasteMapping.getCategory(rawSubclass)

        // Step 2: Decision Logic
        return when {
            // High confidence in subclass - trust mapping over category model
            subConf > SUBCLASS_CONFIDENCE_THRESHOLD -> {
                if (rawCategory != mappedCategory) {
                    Log.w(TAG, "Clash Detected: Model said $rawCategory, Mapping says $mappedCategory for $rawSubclass. Overriding.")
                }
                prediction.copy(category = mappedCategory)
            }

            // Low confidence in both - tag as Uncertain but keep best guesses
            subConf < CATEGORY_CONFIDENCE_THRESHOLD && catConf < CATEGORY_CONFIDENCE_THRESHOLD -> {
                prediction.copy(category = "Uncertain")
            }

            // Otherwise, if they agree, keep it. If they disagree but subConf is mid, prefer mapping if catConf is also low.
            rawCategory != mappedCategory && catConf < 0.7f -> {
                prediction.copy(category = mappedCategory)
            }

            else -> prediction
        }
    }
}
