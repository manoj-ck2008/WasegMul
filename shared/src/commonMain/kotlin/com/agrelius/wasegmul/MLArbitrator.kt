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
 * Pure Kotlin (multiplatform) logic - no Android dependencies.
 */
object MLArbitrator {

    /** Subclass confidence must exceed this to be considered reliable on its own. */
    const val SUBCLASS_CONFIDENCE_THRESHOLD = 0.65f

    /** Below this, a model's output is considered low-confidence. */
    const val LOW_CONFIDENCE_THRESHOLD = 0.50f

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

    /**
     * Arbitrates between visual ML prediction evidence and barcode lookup evidence.
     *
     * Barcode evidence provides high-reliability ground truth. When visual evidence
     * is absent or degraded, barcode evidence is used directly. When both are available,
     * agreement produces a high-confidence consensus result, while disagreement causes
     * the barcode ground truth to override the visual model.
     *
     * @param prediction Visual ML model prediction result, or null if camera inference failed or was skipped.
     * @param barcode Barcode product and packaging evidence.
     * @return Final arbitrated [PredictionResult].
     */
    fun arbitrateWithBarcode(
        prediction: PredictionResult?,
        barcode: BarcodeEvidence
    ): PredictionResult {
        val hasVisualEvidence = prediction != null &&
            (prediction.topCategories.isNotEmpty() || prediction.topSubcategories.isNotEmpty())

        if (!hasVisualEvidence) {
            val confidence = if (barcode.isComplete) 0.98f else 0.80f
            val mode = if (barcode.isComplete) {
                ClassificationMode.BARCODE_GROUND_TRUTH
            } else {
                ClassificationMode.BARCODE_PARTIAL
            }
            val message = MessageGenerator.generateBarcode(
                productName = barcode.productName,
                category = barcode.category,
                subclass = barcode.subclass,
                mode = mode
            )
            return PredictionResult(
                category = barcode.category,
                categoryConfidence = confidence,
                subcategory = barcode.subclass,
                subcategoryConfidence = confidence,
                topSubcategories = listOf(barcode.subclass to confidence),
                topCategories = listOf(barcode.category to confidence),
                classificationMessage = message
            )
        }

        // Visual model output is present
        val visualCategory = resolveVisualCategory(prediction!!)
        val agrees = visualCategory.isNotBlank() && visualCategory.equals(barcode.category.trim(), ignoreCase = true)

        return if (agrees) {
            val mode = ClassificationMode.BARCODE_VISUAL_CONSENSUS
            val confidence = 0.99f
            val message = MessageGenerator.generateBarcode(
                productName = barcode.productName,
                category = barcode.category,
                subclass = barcode.subclass,
                mode = mode
            )
            val mergedCategories = mergeTopList(barcode.category, confidence, prediction.topCategories)
            val mergedSubcategories = mergeTopList(barcode.subclass, confidence, prediction.topSubcategories)
            PredictionResult(
                category = barcode.category,
                categoryConfidence = confidence,
                subcategory = barcode.subclass,
                subcategoryConfidence = confidence,
                topSubcategories = mergedSubcategories,
                topCategories = mergedCategories,
                classificationMessage = message
            )
        } else {
            val mode = ClassificationMode.BARCODE_GROUND_TRUTH
            val confidence = 0.95f
            val message = MessageGenerator.generateBarcode(
                productName = barcode.productName,
                category = barcode.category,
                subclass = barcode.subclass,
                mode = mode
            )
            PredictionResult(
                category = barcode.category,
                categoryConfidence = confidence,
                subcategory = barcode.subclass,
                subcategoryConfidence = confidence,
                topSubcategories = listOf(barcode.subclass to confidence),
                topCategories = listOf(barcode.category to confidence),
                classificationMessage = message
            )
        }
    }

    private fun resolveVisualCategory(prediction: PredictionResult): String {
        val cat = prediction.category.trim()
        if (cat.isNotBlank() && !cat.equals(WasteMapping.UNKNOWN, ignoreCase = true) && !cat.equals(WasteMapping.UNCERTAIN, ignoreCase = true)) {
            return cat
        }
        val topCat = prediction.topCategories.firstOrNull()?.first?.trim()
        if (!topCat.isNullOrBlank() && !topCat.equals(WasteMapping.UNKNOWN, ignoreCase = true) && !topCat.equals(WasteMapping.UNCERTAIN, ignoreCase = true)) {
            return topCat
        }
        val sub = prediction.subcategory.trim()
        if (sub.isNotBlank() && !sub.equals(WasteMapping.UNKNOWN, ignoreCase = true) && !sub.equals(WasteMapping.UNCERTAIN, ignoreCase = true)) {
            val mapped = WasteMapping.getCategory(sub)
            if (mapped != WasteMapping.UNKNOWN && mapped != WasteMapping.UNCERTAIN) {
                return mapped
            }
        }
        val topSub = prediction.topSubcategories.firstOrNull()?.first?.trim()
        if (!topSub.isNullOrBlank()) {
            val mapped = WasteMapping.getCategory(topSub)
            if (mapped != WasteMapping.UNKNOWN && mapped != WasteMapping.UNCERTAIN) {
                return mapped
            }
        }
        return cat
    }

    private fun mergeTopList(
        primaryLabel: String,
        primaryConfidence: Float,
        existingList: List<Pair<String, Float>>
    ): List<Pair<String, Float>> {
        val remaining = existingList.filterNot { it.first.equals(primaryLabel, ignoreCase = true) }
        return listOf(primaryLabel to primaryConfidence) + remaining
    }

    // ── Full pipeline (both models available) ─────────────────────────────────

    private fun arbitrateFull(prediction: PredictionResult): PredictionResult {
        val rawCategory = prediction.category.trim()
        if (rawCategory.isBlank()) {
            // Blank category with valid subclass evidence -> use subclass-only path,
            // not neither (which would discard evidence).
            return if (prediction.topSubcategories.isNotEmpty()) {
                arbitrateSubclassOnly(prediction)
            } else {
                arbitrateNeither(prediction)
            }
        }
        val catConf = prediction.categoryConfidence
        val subConf = prediction.subcategoryConfidence
        val mappedCategory = WasteMapping.getCategory(prediction.subcategory)

        // Unmapped subclass → try to preserve the category if it's valid.
        if (mappedCategory == WasteMapping.UNKNOWN) {
            // If the category itself is valid, keep it but DO NOT fabricate a
            // subclass equal to the category (no such subclass exists). Use
            // UNCERTAIN subclass so KB fallback shows honest category-level guidance.
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
                    subcategory = WasteMapping.UNCERTAIN,
                    subcategoryConfidence = 0f,
                    classificationMessage = msg
                )
            }
            // Category is also unknown - genuinely don't know.
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

        // Both models agree on the category (case-insensitive)
        if (rawCategory.equals(mappedCategory, ignoreCase = true)) {
            if (catConf < LOW_CONFIDENCE_THRESHOLD && subConf < LOW_CONFIDENCE_THRESHOLD) {
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
                    subcategory = WasteMapping.UNCERTAIN,
                    categoryConfidence = 0f,
                    subcategoryConfidence = 0f,
                    classificationMessage = msg
                )
            }

            val mode = if (catConf >= 0.80f && subConf >= 0.80f) {
                ClassificationMode.BOTH_AGREE_HIGH
            } else {
                ClassificationMode.BOTH_AGREE
            }
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

        // Models disagree → category model takes priority unless it is confused.

        // If category itself is Unknown/Uncertain but subclass maps to a valid
        // category, trust the subclass evidence instead of discarding it.
        if ((rawCategory.equals(WasteMapping.UNKNOWN, ignoreCase = true) || rawCategory.equals(WasteMapping.UNCERTAIN, ignoreCase = true)) &&
            mappedCategory != WasteMapping.UNKNOWN && mappedCategory != WasteMapping.UNCERTAIN
        ) {
            return arbitrateSubclassOnly(prediction)
        }

        // Check entropy: if the category model is truly confused, check both models' confidence.
        val catEntropy = computeEntropy(prediction.topCategories)
        val categoryModelConfused = catEntropy > CATEGORY_ENTROPY_CEILING || catConf < LOW_CONFIDENCE_THRESHOLD

        // If category model is confused but subclass model is highly confident (>= SUBCLASS_CONFIDENCE_THRESHOLD),
        // trust the reliable subclass model over the confused category model!
        if (categoryModelConfused && subConf >= SUBCLASS_CONFIDENCE_THRESHOLD && mappedCategory != WasteMapping.UNKNOWN) {
            return arbitrateSubclassOnly(prediction)
        }

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
                subcategory = WasteMapping.UNCERTAIN,
                categoryConfidence = 0f,
                subcategoryConfidence = 0f,
                classificationMessage = msg
            )
        }

        // Cross-check: does any subclass prediction map to the category model's result?
        val matchingSubclass = prediction.topSubcategories
            .filter { WasteMapping.getCategory(it.first).equals(rawCategory, ignoreCase = true) }
            .maxByOrNull { it.second }

        return if (matchingSubclass != null && matchingSubclass.second >= LOW_CONFIDENCE_THRESHOLD) {
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
            // No matching subclass → category-level result only (honest UNCERTAIN subclass).
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
                subcategory = WasteMapping.UNCERTAIN,
                subcategoryConfidence = 0f,
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
                subcategory = WasteMapping.UNKNOWN,
                catConfidence = catConf,
                subConfidence = 0f,
                topSubcategories = emptyList(),
                topCategories = prediction.topCategories,
                mode = ClassificationMode.BOTH_UNCERTAIN
            )
            return prediction.copy(
                category = WasteMapping.UNCERTAIN,
                subcategory = WasteMapping.UNCERTAIN,
                categoryConfidence = 0f,
                subcategoryConfidence = 0f,
                classificationMessage = msg
            )
        }
        val msg = MessageGenerator.generate(
            category = prediction.category,
            subcategory = WasteMapping.UNKNOWN,
            catConfidence = catConf,
            subConfidence = 0f,
            topSubcategories = emptyList(),
            topCategories = prediction.topCategories,
            mode = ClassificationMode.CATEGORY_ONLY
        )
        return prediction.copy(
            subcategory = WasteMapping.UNCERTAIN,
            subcategoryConfidence = 0f,
            classificationMessage = msg
        )
    }

    // ── Degraded: subclass-only (category model failed) ──────────────────────

    private fun arbitrateSubclassOnly(prediction: PredictionResult): PredictionResult {
        val subConf = prediction.subcategoryConfidence
        val mappedCategory = WasteMapping.getCategory(prediction.subcategory)

        if (mappedCategory == WasteMapping.UNKNOWN) {
            val msg = MessageGenerator.generate(
                category = WasteMapping.UNKNOWN,
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
                subcategory = WasteMapping.UNCERTAIN,
                categoryConfidence = 0f,
                subcategoryConfidence = 0f,
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
     * [topPredictions]. Remaining probability mass is lumped into a single "rest" bucket
     * which OVERESTIMATES certainty (underestimates true entropy) - callers must treat
     * this as a lower bound.
     */
    fun computeEntropy(topPredictions: List<Pair<String, Float>>): Float {
        if (topPredictions.isEmpty()) return 0f
        val raw = topPredictions.map { it.second.coerceAtLeast(0f).toDouble() }
        val sum = raw.sum()
        if (sum <= 0.0) return 0f
        val normalized = if (sum > 1.0) raw.map { it / sum } else raw
        val accounted = normalized.sum()
        val rest = (1.0 - accounted).coerceAtLeast(0.0)
        val probs = normalized + listOfNotNull(rest.takeIf { it > 1e-6 })
        return probs.sumOf { p ->
            if (p > 0.0) -p * ln(p) / ln(2.0) else 0.0
        }.toFloat()
    }
}
