package com.agrelius.wasegmul

import kotlin.math.ln

/**
 * Smart router that arbitrates between the Category and Subclass models to produce
 * a single, consistent classification result.
 *
 * ## Design principle: Category model is the source of truth
 *
 * The category classifier (~95% accuracy, trained on 86k images) is more reliable than
 * the subclass classifier (~86-88%). When the two disagree, we:
 *
 *  1. Trust the category model's classification.
 *  2. Cross-check the subclass model's top-5 predictions for one that maps to the
 *     category. If found, use that subclass (not the subclass model's #1 pick).
 *  3. If no subclass prediction matches the category, fall back to category-level
 *     identification (coarse-grained result).
 *
 * This prevents contradictory results like category="E-Waste" + subcategory="Plastic".
 *
 * ## Degraded modes
 *
 * When one model fails, the other's output is used with appropriate caveats.
 * Every case generates a dynamic user-facing message via [MessageGenerator].
 *
 * Pure Kotlin (multiplatform) logic — no Android dependencies.
 */
object MLArbitrator {

    /** Subclass confidence must exceed this to be considered reliable on its own. */
    private const val SUBCLASS_CONFIDENCE_THRESHOLD = 0.65f

    /** Below this, a model's output is considered low-confidence. */
    private const val LOW_CONFIDENCE_THRESHOLD = 0.50f

    /** Maximum acceptable entropy (bits) for a 4-class distribution to be considered "certain". */
    private const val CATEGORY_ENTROPY_CEILING = 1.2f

    fun arbitrate(prediction: PredictionResult): PredictionResult {
        val hasCat = prediction.topCategories.isNotEmpty()
        val hasSub = prediction.topSubcategories.isNotEmpty()

        return when {
            !hasCat && !hasSub -> arbitrateNeither(prediction)
            hasCat && !hasSub -> arbitrateCategoryOnly(prediction)
            !hasCat && hasSub -> arbitrateSubclassOnly(prediction)
            else -> arbitrateFull(prediction)
        }
    }

    // ── Full pipeline (both models available) ─────────────────────────────────

    private fun arbitrateFull(prediction: PredictionResult): PredictionResult {
        val rawCategory = prediction.category
        val catConf = prediction.categoryConfidence
        val subConf = prediction.subcategoryConfidence
        val mappedCategory = WasteMapping.getCategory(prediction.subcategory)

        // Unmapped subclass → try to preserve the category if it's valid.
        if (mappedCategory == WasteMapping.UNKNOWN) {
            // If the category itself is valid, keep it and fall back to category-level result.
            if (rawCategory != WasteMapping.UNKNOWN && rawCategory != WasteMapping.UNCERTAIN) {
                val msg = MessageGenerator.generate(
                    category = rawCategory,
                    subcategory = prediction.subcategory,
                    catConfidence = catConf,
                    subConfidence = subConf,
                    topSubcategories = prediction.topSubcategories,
                    topCategories = prediction.topCategories,
                    mode = ClassificationMode.CATEGORY_OVERRIDE_NO_MATCH
                )
                return prediction.copy(
                    category = rawCategory,
                    subcategory = rawCategory,
                    subcategoryConfidence = catConf,
                    classificationMessage = msg
                )
            }
            // Category is also unknown — genuinely don't know.
            val msg = MessageGenerator.generate(
                category = rawCategory,
                subcategory = prediction.subcategory,
                catConfidence = catConf,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = prediction.topCategories,
                mode = ClassificationMode.UNMAPPED_SUBCLASS
            )
            return prediction.copy(
                category = WasteMapping.UNKNOWN,
                classificationMessage = msg
            )
        }

        // Both models agree on the category → high confidence.
        if (rawCategory == mappedCategory) {
            val mode = if (catConf > 0.85f && subConf > SUBCLASS_CONFIDENCE_THRESHOLD)
                ClassificationMode.BOTH_AGREE_HIGH else ClassificationMode.BOTH_AGREE
            val msg = MessageGenerator.generate(
                category = rawCategory,
                subcategory = prediction.subcategory,
                catConfidence = catConf,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = prediction.topCategories,
                mode = mode
            )
            return prediction.copy(
                category = rawCategory,
                classificationMessage = msg
            )
        }

        // Models disagree → category model takes priority.

        // Check entropy: if the category model is truly confused, check both models' confidence.
        val catEntropy = computeEntropy(prediction.topCategories)
        val categoryModelConfused = catEntropy > CATEGORY_ENTROPY_CEILING && catConf < LOW_CONFIDENCE_THRESHOLD

        if (categoryModelConfused && subConf < LOW_CONFIDENCE_THRESHOLD) {
            // Both models are uncertain.
            val msg = MessageGenerator.generate(
                category = rawCategory,
                subcategory = prediction.subcategory,
                catConfidence = catConf,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = prediction.topCategories,
                mode = ClassificationMode.BOTH_UNCERTAIN
            )
            return prediction.copy(
                category = WasteMapping.UNCERTAIN,
                classificationMessage = msg
            )
        }

        // Cross-check: does any subclass prediction map to the category model's result?
        val matchingSubclass = prediction.topSubcategories
            .filter { WasteMapping.getCategory(it.first) == rawCategory }
            .maxByOrNull { it.second }

        return if (matchingSubclass != null && matchingSubclass.second > LOW_CONFIDENCE_THRESHOLD) {
            // Found a subclass that matches the category → use it.
            val msg = MessageGenerator.generate(
                category = rawCategory,
                subcategory = prediction.subcategory,
                catConfidence = catConf,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = prediction.topCategories,
                mode = ClassificationMode.CATEGORY_OVERRIDE_MATCH
            )
            prediction.copy(
                category = rawCategory,
                subcategory = matchingSubclass.first,
                subcategoryConfidence = matchingSubclass.second,
                classificationMessage = msg
            )
        } else {
            // No matching subclass → category-level result only.
            val msg = MessageGenerator.generate(
                category = rawCategory,
                subcategory = prediction.subcategory,
                catConfidence = catConf,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = prediction.topCategories,
                mode = ClassificationMode.CATEGORY_OVERRIDE_NO_MATCH
            )
            prediction.copy(
                category = rawCategory,
                subcategory = rawCategory,
                subcategoryConfidence = catConf,
                classificationMessage = msg
            )
        }
    }

    // ── Degraded: category-only (subclass model failed) ──────────────────────

    private fun arbitrateCategoryOnly(prediction: PredictionResult): PredictionResult {
        val catConf = prediction.categoryConfidence
        if (catConf < LOW_CONFIDENCE_THRESHOLD) {
            val msg = MessageGenerator.generate(
                category = prediction.category,
                subcategory = "Unknown",
                catConfidence = catConf,
                subConfidence = 0f,
                topSubcategories = emptyList(),
                topCategories = prediction.topCategories,
                mode = ClassificationMode.BOTH_UNCERTAIN
            )
            return prediction.copy(
                category = WasteMapping.UNCERTAIN,
                subcategory = WasteMapping.UNCERTAIN,
                classificationMessage = msg
            )
        }
        val msg = MessageGenerator.generate(
            category = prediction.category,
            subcategory = "Unknown",
            catConfidence = catConf,
            subConfidence = 0f,
            topSubcategories = emptyList(),
            topCategories = prediction.topCategories,
            mode = ClassificationMode.CATEGORY_ONLY
        )
        return prediction.copy(
            subcategory = prediction.category,
            subcategoryConfidence = catConf,
            classificationMessage = msg
        )
    }

    // ── Degraded: subclass-only (category model failed) ──────────────────────

    private fun arbitrateSubclassOnly(prediction: PredictionResult): PredictionResult {
        val subConf = prediction.subcategoryConfidence
        val mappedCategory = WasteMapping.getCategory(prediction.subcategory)

        if (mappedCategory == WasteMapping.UNKNOWN) {
            val msg = MessageGenerator.generate(
                category = "Unknown",
                subcategory = prediction.subcategory,
                catConfidence = 0f,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = emptyList(),
                mode = ClassificationMode.UNMAPPED_SUBCLASS
            )
            return prediction.copy(
                category = WasteMapping.UNKNOWN,
                classificationMessage = msg
            )
        }
        if (subConf < LOW_CONFIDENCE_THRESHOLD) {
            val msg = MessageGenerator.generate(
                category = WasteMapping.UNCERTAIN,
                subcategory = prediction.subcategory,
                catConfidence = 0f,
                subConfidence = subConf,
                topSubcategories = prediction.topSubcategories,
                topCategories = emptyList(),
                mode = ClassificationMode.BOTH_UNCERTAIN
            )
            return prediction.copy(
                category = WasteMapping.UNCERTAIN,
                classificationMessage = msg
            )
        }
        val msg = MessageGenerator.generate(
            category = mappedCategory,
            subcategory = prediction.subcategory,
            catConfidence = 0f,
            subConfidence = subConf,
            topSubcategories = prediction.topSubcategories,
            topCategories = emptyList(),
            mode = ClassificationMode.SUBCLASS_ONLY
        )
        return prediction.copy(
            category = mappedCategory,
            categoryConfidence = subConf,
            classificationMessage = msg
        )
    }

    // ── Neither model produced output ─────────────────────────────────────────

    private fun arbitrateNeither(prediction: PredictionResult): PredictionResult {
        val msg = MessageGenerator.generate(
            category = WasteMapping.UNCERTAIN,
            subcategory = WasteMapping.UNCERTAIN,
            catConfidence = 0f,
            subConfidence = 0f,
            topSubcategories = emptyList(),
            topCategories = emptyList(),
            mode = ClassificationMode.BOTH_UNCERTAIN
        )
        return prediction.copy(
            category = WasteMapping.UNCERTAIN,
            subcategory = WasteMapping.UNCERTAIN,
            classificationMessage = msg
        )
    }

    // ── Entropy estimation ───────────────────────────────────────────────────

    /**
     * Computes Shannon entropy (in bits) over the probability distribution implied by
     * [topPredictions]. The remaining probability mass (beyond the top-K) is conservatively
     * lumped into a single "rest" bucket.
     */
    fun computeEntropy(topPredictions: List<Pair<String, Float>>): Float {
        if (topPredictions.isEmpty()) return 0f
        val accounted = topPredictions.sumOf { it.second.toDouble() }.toFloat()
        val rest = (1f - accounted).coerceAtLeast(0f)
        val probs = topPredictions.map { it.second } + listOfNotNull(rest.takeIf { it > 0f })
        return probs.sumOf { p ->
            if (p > 0f) -p * ln(p) / ln(2.0) else 0.0
        }.toFloat()
    }
}
