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
 * ## UNKNOWN vs UNCERTAIN contract
 * - `Unknown` ([WasteMapping.UNKNOWN]): the label is NOT in the taxonomy (unmapped
 *   subclass, rejected category). Emitted with zeroed confidences and raw inputs
 *   preserved in [PredictionResult.rawCategoryConfidence]/[PredictionResult.rawSubcategoryConfidence].
 * - `Uncertain` ([WasteMapping.UNCERTAIN]): taxonomy known, confidence too low to commit.
 *   Category-level guidance MAY still be shown when the category is reliable.
 * Note: [WasteMapping.getCategory] never returns `Uncertain` (unmapped → `Unknown`),
 * so every explicit `Uncertain` in an output is a deliberate low-confidence verdict,
 * never a lookup miss.
 *
 * Pure Kotlin (multiplatform) logic - no Android dependencies.
 */
object MLArbitrator {

    /**
     * Floor for trusting the subclass model over a confused category model.
     * The EFFECTIVE bar tracks the user's strictness: `max(SUBCLASS_CONFIDENCE_THRESHOLD, low)`
     * where `low` is the Settings slider — strict users (low = 0.95) get UNCERTAIN instead
     * of forced subclass guesses. The constant is a floor, not a fixed bar.
     */
    const val SUBCLASS_CONFIDENCE_THRESHOLD = 0.65f

    /** Below this, a model's output is considered low-confidence. */
    const val LOW_CONFIDENCE_THRESHOLD = 0.50f

    /** Number of category classes the entropy ceiling is derived from. */
    const val CATEGORY_CLASS_COUNT = 4

    /**
     * Maximum Shannon entropy (bits) for a [CATEGORY_CLASS_COUNT]-class distribution to be
     * considered "certain": `log2(4) = 2.0` — the entropy of a perfectly uniform distribution
     * over the four runtime categories. Rationale: a model whose output is indistinguishable
     * from uniform guessing is, by definition, confused. Computed from the actual class
     * count rather than a magic number (prior value 1.2 was unjustified for 4 classes).
     */
    val CATEGORY_ENTROPY_CEILING: Float = (ln(CATEGORY_CLASS_COUNT.toDouble()) / ln(2.0)).toFloat()

    /**
     * Dual-model agreement counts as HIGH only when BOTH confidences reach this.
     * Rationale: 0.80 keeps the "highly dependable" message pool honest (two independent
     * models at ≥0.80 dominate the error budget of either alone) while remaining reachable
     * — the category model operates at ~95% top-1. Below this, agreement uses the hedged
     * standard pool ([MessageGenerator] case 1b).
     */
    const val BOTH_AGREE_HIGH_THRESHOLD = 0.80f

    /** Cap for merged top-K evidence lists (analytics evidence, not distributions). */
    const val MAX_MERGED_TOPS = 10

    /** Barcode with fully-resolved packaging metadata: near-ground-truth confidence. */
    const val BARCODE_COMPLETE_CONFIDENCE = 0.98f

    /** Barcode with partial packaging metadata: high but explicitly discounted. */
    const val BARCODE_PARTIAL_CONFIDENCE = 0.80f

    /** Barcode + visual models independently agree: highest confidence in the system. */
    const val BARCODE_CONSENSUS_CONFIDENCE = 0.99f

    /** Barcode overrides a disagreeing visual model: ground truth wins, small discount for residual doubt. */
    const val BARCODE_OVERRIDE_CONFIDENCE = 0.95f

    /**
     * A visual top-category entry counts toward barcode agreement only at or above this
     * confidence. Rationale: mere PRESENCE in the top list (e.g. Recyclable @0.10) is not
     * agreement — below 0.50 the visual model itself would not commit to that category,
     * so promoting it to a 0.99 cross-modal consensus would fabricate certainty.
     */
    const val BARCODE_TOP_AGREEMENT_FLOOR = 0.50f

    fun arbitrate(prediction: PredictionResult): PredictionResult =
        arbitrate(prediction, LOW_CONFIDENCE_THRESHOLD)

    /**
     * User-tunable entry point: [lowThreshold] comes from Settings → confidence slider
     * (0.10..0.95). The subclass high-confidence bar tracks it
     * (`max(SUBCLASS_CONFIDENCE_THRESHOLD, low)`) so strict users get more
     * UNCERTAIN results instead of forced guesses. Non-finite input falls back to
     * [LOW_CONFIDENCE_THRESHOLD]; the range is coerced to `0.05..0.95`.
     * Input is sanitised ([PredictionResult.sanitized]) before any logic runs, so NaN or
     * uncalibrated confidences degrade to UNCERTAIN instead of poisoning comparisons
     * (every `< NaN` is false — sanitising first is what makes the guards sound).
     */
    fun arbitrate(prediction: PredictionResult, lowThreshold: Float): PredictionResult {
        val low = if (lowThreshold.isFinite()) lowThreshold.coerceIn(0.05f, 0.95f)
        else LOW_CONFIDENCE_THRESHOLD
        val clean = prediction.sanitized()
        val hasCat = clean.topCategories.isNotEmpty()
        val hasSub = clean.topSubcategories.isNotEmpty()

        return when {
            !hasCat && !hasSub -> arbitrateNeither(clean)
            hasCat && !hasSub -> arbitrateCategoryOnly(clean, low)
            !hasCat && hasSub -> arbitrateSubclassOnly(clean, low)
            else -> arbitrateFull(clean, low)
        }
    }

    /**
     * Arbitrates between visual ML prediction evidence and barcode lookup evidence.
     *
     * Barcode evidence provides high-reliability ground truth, but it is VALIDATED, not
     * blind-trusted: a barcode category outside the taxonomy ([WasteMapping.isKnownCategory],
     * sentinels excluded) is rejected — visual evidence is used alone when present, else an
     * honest UNKNOWN is returned. A blank product name renders neutrally as "this product"
     * (the API never invents names; direct [MessageGenerator.generateBarcode] calls still
     * require a real name).
     *
     * Agreement is checked against BOTH the visual headline category and its top-category
     * list (unified — the verdict no longer depends on which field the pipeline populated).
     * On disagreement the barcode wins the verdict BUT the visual top lists are preserved
     * as merged evidence (barcode entry first, capped at [MAX_MERGED_TOPS]) so the conflict
     * stays visible to analytics instead of being discarded.
     *
     * @param prediction Visual ML model prediction result, or null if camera inference failed or was skipped.
     * @param barcode Barcode product and packaging evidence.
     * @return Final arbitrated [PredictionResult].
     */
    fun arbitrateWithBarcode(
        prediction: PredictionResult?,
        barcode: BarcodeEvidence
    ): PredictionResult {
        val clean = prediction?.sanitized()
        val hasVisualEvidence = clean != null &&
            (clean.topCategories.isNotEmpty() || clean.topSubcategories.isNotEmpty())
        val displayName = barcode.productName.ifBlank { "this product" }
        val barcodeCat = barcode.category.trim()
        // Fail-closed taxonomy validation: unknown/sentinel/blank barcode categories are rejected.
        val validBarcodeCat = barcodeCat.takeIf {
            WasteMapping.isKnownCategory(it) && !WasteMapping.isSentinel(it)
        }

        if (validBarcodeCat == null) {
            if (hasVisualEvidence) return arbitrate(clean!!)
            val msg = MessageGenerator.generate(
                category = WasteMapping.UNKNOWN,
                subcategory = barcode.subclass,
                catConfidence = 0f,
                subConfidence = 0f,
                topSubcategories = emptyList(),
                topCategories = emptyList(),
                mode = ClassificationMode.UNMAPPED_SUBCLASS
            )
            return PredictionResult(
                category = WasteMapping.UNKNOWN,
                categoryConfidence = 0f,
                subcategory = barcode.subclass,
                subcategoryConfidence = 0f,
                classificationMessage = msg
            )
        }

        if (!hasVisualEvidence) {
            val confidence = if (barcode.isComplete) BARCODE_COMPLETE_CONFIDENCE else BARCODE_PARTIAL_CONFIDENCE
            val mode = if (barcode.isComplete) {
                ClassificationMode.BARCODE_GROUND_TRUTH
            } else {
                ClassificationMode.BARCODE_PARTIAL
            }
            val message = MessageGenerator.generateBarcode(
                productName = displayName,
                category = validBarcodeCat,
                subclass = barcode.subclass,
                mode = mode
            )
            return PredictionResult(
                category = validBarcodeCat,
                categoryConfidence = confidence,
                subcategory = barcode.subclass,
                subcategoryConfidence = confidence,
                topSubcategories = listOf(barcode.subclass to confidence),
                topCategories = listOf(validBarcodeCat to confidence),
                classificationMessage = message
            )
        }

        // Visual model output is present. Agreement is unified across fields, but a
        // top-list mention only counts when the visual model actually commits to it
        // (>= BARCODE_TOP_AGREEMENT_FLOOR) — a 0.10 also-ran must not trigger consensus.
        val visualCategory = resolveVisualCategory(clean!!)
        val topCatAgrees = clean.topCategories.any { (label, conf) ->
            conf.isFinite() && conf >= BARCODE_TOP_AGREEMENT_FLOOR &&
                label.equals(validBarcodeCat, ignoreCase = true)
        }
        val agrees = (visualCategory.isNotBlank() && visualCategory.equals(validBarcodeCat, ignoreCase = true)) ||
            topCatAgrees

        return if (agrees) {
            val mode = ClassificationMode.BARCODE_VISUAL_CONSENSUS
            val confidence = BARCODE_CONSENSUS_CONFIDENCE
            val message = MessageGenerator.generateBarcode(
                productName = displayName,
                category = validBarcodeCat,
                subclass = barcode.subclass,
                mode = mode
            )
            val mergedCategories = mergeTopList(validBarcodeCat, confidence, clean.topCategories)
            val mergedSubcategories = mergeTopList(barcode.subclass, confidence, clean.topSubcategories)
            PredictionResult(
                category = validBarcodeCat,
                categoryConfidence = confidence,
                subcategory = barcode.subclass,
                subcategoryConfidence = confidence,
                topSubcategories = mergedSubcategories,
                topCategories = mergedCategories,
                classificationMessage = message
            )
        } else {
            val mode = ClassificationMode.BARCODE_GROUND_TRUTH
            val confidence = BARCODE_OVERRIDE_CONFIDENCE
            val message = MessageGenerator.generateBarcode(
                productName = displayName,
                category = validBarcodeCat,
                subclass = barcode.subclass,
                mode = mode
            )
            // Barcode wins the verdict; visual evidence is preserved (merged, capped) so the
            // disagreement signal survives for analytics instead of being discarded.
            PredictionResult(
                category = validBarcodeCat,
                categoryConfidence = confidence,
                subcategory = barcode.subclass,
                subcategoryConfidence = confidence,
                topSubcategories = mergeTopList(barcode.subclass, confidence, clean.topSubcategories),
                topCategories = mergeTopList(validBarcodeCat, confidence, clean.topCategories),
                classificationMessage = message
            )
        }
    }

    private fun resolveVisualCategory(prediction: PredictionResult): String {
        val cat = prediction.category.trim()
        if (cat.isNotBlank() && !WasteMapping.isSentinel(cat)) {
            return cat
        }
        val topCat = prediction.topCategories.firstOrNull()?.first?.trim()
        if (!topCat.isNullOrBlank() && !WasteMapping.isSentinel(topCat)) {
            return topCat
        }
        val sub = prediction.subcategory.trim()
        if (sub.isNotBlank() && !WasteMapping.isSentinel(sub)) {
            val mapped = WasteMapping.getCategory(sub)
            if (!WasteMapping.isSentinel(mapped) && mapped != WasteMapping.UNKNOWN) {
                return mapped
            }
        }
        val topSub = prediction.topSubcategories.firstOrNull()?.first?.trim()
        if (!topSub.isNullOrBlank()) {
            val mapped = WasteMapping.getCategory(topSub)
            if (!WasteMapping.isSentinel(mapped) && mapped != WasteMapping.UNKNOWN) {
                return mapped
            }
        }
        return cat
    }

    /**
     * Merges a primary (label, confidence) entry ahead of an existing evidence list.
     * Result is capped at [MAX_MERGED_TOPS], non-finite entries are dropped, remaining
     * entries are de-duplicated (case-insensitive, best confidence kept) and sorted
     * descending. This is EVIDENCE, not a probability distribution: confidences come from
     * different sources and their sum may exceed 1 — never feed it to entropy or treat it
     * as normalised.
     */
    internal fun mergeTopList(
        primaryLabel: String,
        primaryConfidence: Float,
        existingList: List<Pair<String, Float>>
    ): List<Pair<String, Float>> {
        val primary = primaryLabel.trim() to primaryConfidence.coerceIn(0f, 1f)
        val rest = existingList
            .filter { (label, conf) ->
                label.isNotBlank() && conf.isFinite() &&
                    !label.equals(primary.first, ignoreCase = true)
            }
            .groupBy { it.first.lowercase() }
            .map { (_, group) -> group.maxByOrNull { it.second }!! }
            .sortedByDescending { it.second }
        return (listOf(primary) + rest).take(MAX_MERGED_TOPS)
    }

    // ── Full pipeline (both models available) ─────────────────────────────────

    private fun arbitrateFull(prediction: PredictionResult, low: Float = LOW_CONFIDENCE_THRESHOLD): PredictionResult {
        val rawCategory = prediction.category.trim()
        if (rawCategory.isBlank()) {
            // Blank category with valid subclass evidence -> use subclass-only path,
            // not neither (which would discard evidence).
            return if (prediction.topSubcategories.isNotEmpty()) {
                arbitrateSubclassOnly(prediction, low)
            } else {
                arbitrateNeither(prediction)
            }
        }
        // Reject categories outside the taxonomy (fail-closed): never pass "FooBar" @0.99
        // through as a verdict. There is no platform logger in commonMain; the UNKNOWN
        // verdict itself is the signal — platform layers log verdicts centrally.
        if (!WasteMapping.isKnownCategory(rawCategory) || WasteMapping.isSentinel(rawCategory)) {
            if (WasteMapping.isSentinel(rawCategory)) {
                // Headline is a sentinel but tops may hold real evidence — let the
                // subclass path try to rescue it.
                val mapped = WasteMapping.getCategory(prediction.subcategory)
                if (mapped != WasteMapping.UNKNOWN && !WasteMapping.isSentinel(mapped)) {
                    return arbitrateSubclassOnly(prediction, low)
                }
            }
            val msg = MessageGenerator.generate(
                category = WasteMapping.UNKNOWN,
                subcategory = prediction.subcategory,
                catConfidence = 0f,
                subConfidence = prediction.subcategoryConfidence,
                topSubcategories = prediction.topSubcategories,
                topCategories = prediction.topCategories,
                mode = ClassificationMode.UNMAPPED_SUBCLASS
            )
            return prediction.copy(
                category = WasteMapping.UNKNOWN,
                categoryConfidence = 0f,
                subcategoryConfidence = 0f,
                rawCategoryConfidence = prediction.categoryConfidence.coerceIn(0f, 1f),
                rawSubcategoryConfidence = prediction.subcategoryConfidence.coerceIn(0f, 1f),
                classificationMessage = msg
            )
        }
        val catConf = prediction.categoryConfidence
        val subConf = prediction.subcategoryConfidence
        val mappedPrimary = WasteMapping.getCategory(prediction.subcategory)
        // Unified agreement: the headline subclass mapping OR the best top-list entry.
        // The verdict no longer depends on which field the pipeline happened to populate.
        val bestTopMatch = prediction.topSubcategories
            .filter { it.first.isNotBlank() && it.second.isFinite() }
            .filter { WasteMapping.getCategory(it.first).equals(rawCategory, ignoreCase = true) }
            .maxByOrNull { it.second }
        val primaryAgrees = rawCategory.equals(mappedPrimary, ignoreCase = true)
        val agrees = primaryAgrees || bestTopMatch != null

        // Unmapped subclass → try to preserve the category if it's valid.
        if (mappedPrimary == WasteMapping.UNKNOWN && bestTopMatch == null) {
            // If the category itself is valid, keep it but DO NOT fabricate a
            // subclass equal to the category (no such subclass exists). Use
            // UNCERTAIN subclass so KB fallback shows honest category-level guidance.
            if (!WasteMapping.isSentinel(rawCategory)) {
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

        // Both models agree on the category (case-insensitive, unified fields)
        if (agrees) {
            if (catConf < low && subConf < low) {
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
                    rawCategoryConfidence = catConf,
                    rawSubcategoryConfidence = subConf,
                    classificationMessage = msg
                )
            }

            // Agreement via the top list but NOT the headline subclass: adopt the matching
            // top entry instead of emitting a contradictory category+subclass pair.
            if (!primaryAgrees && bestTopMatch != null) {
                val msg = MessageGenerator.generate(
                    category = rawCategory,
                    subcategory = bestTopMatch.first,
                    catConfidence = catConf,
                    subConfidence = bestTopMatch.second,
                    topSubcategories = prediction.topSubcategories,
                    topCategories = prediction.topCategories,
                    mode = ClassificationMode.CATEGORY_OVERRIDE_MATCH
                )
                return prediction.copy(
                    category = rawCategory,
                    subcategory = bestTopMatch.first,
                    subcategoryConfidence = bestTopMatch.second,
                    classificationMessage = msg
                )
            }

            val mode = if (catConf >= BOTH_AGREE_HIGH_THRESHOLD && subConf >= BOTH_AGREE_HIGH_THRESHOLD) {
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
        if (WasteMapping.isSentinel(rawCategory) &&
            mappedPrimary != WasteMapping.UNKNOWN && !WasteMapping.isSentinel(mappedPrimary)
        ) {
            return arbitrateSubclassOnly(prediction, low)
        }

        // Check entropy: if the category model is truly confused, check both models' confidence.
        val catEntropy = computeEntropy(prediction.topCategories)
        val categoryModelConfused = catEntropy > CATEGORY_ENTROPY_CEILING || catConf < low

        // If category model is confused but subclass model clears the (threshold-tracking)
        // bar, trust the reliable subclass model over the confused category model.
        // The bar tracks the user's strictness so strict settings force UNCERTAIN instead.
        val subclassBar = maxOf(SUBCLASS_CONFIDENCE_THRESHOLD, low)
        if (categoryModelConfused && subConf >= subclassBar && mappedPrimary != WasteMapping.UNKNOWN) {
            return arbitrateSubclassOnly(prediction, low)
        }

        if (categoryModelConfused && subConf < low) {
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
                rawCategoryConfidence = catConf,
                rawSubcategoryConfidence = subConf,
                classificationMessage = msg
            )
        }

        // Cross-check: does any subclass prediction map to the category model's result?
        val matchingSubclass = bestTopMatch

        return if (matchingSubclass != null && matchingSubclass.second >= low) {
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

    private fun arbitrateCategoryOnly(prediction: PredictionResult, low: Float = LOW_CONFIDENCE_THRESHOLD): PredictionResult {
        // Fail-closed taxonomy validation: a headline outside the taxonomy ("FooBar",
        // blank, sentinel) is rejected. When the headline is garbage but the top list
        // holds a valid leader, rescue the leader instead of discarding the evidence.
        val headline = prediction.category.trim()
        val topLeader = prediction.topCategories
            .filter { it.first.isNotBlank() && it.second.isFinite() }
            .maxByOrNull { it.second }
        val effective = when {
            WasteMapping.isKnownCategory(headline) && !WasteMapping.isSentinel(headline) ->
                headline to prediction.categoryConfidence
            topLeader != null && WasteMapping.isKnownCategory(topLeader.first) &&
                !WasteMapping.isSentinel(topLeader.first) ->
                topLeader.first.trim() to topLeader.second
            else -> null
        }
        if (effective == null) {
            val msg = MessageGenerator.generate(
                category = WasteMapping.UNKNOWN,
                subcategory = WasteMapping.UNKNOWN,
                catConfidence = 0f,
                subConfidence = 0f,
                topSubcategories = emptyList(),
                topCategories = prediction.topCategories,
                mode = ClassificationMode.UNMAPPED_SUBCLASS
            )
            return prediction.copy(
                category = WasteMapping.UNKNOWN,
                subcategory = WasteMapping.UNKNOWN,
                categoryConfidence = 0f,
                subcategoryConfidence = 0f,
                rawCategoryConfidence = prediction.categoryConfidence.coerceIn(0f, 1f),
                rawSubcategoryConfidence = prediction.subcategoryConfidence.coerceIn(0f, 1f),
                classificationMessage = msg
            )
        }
        val (cat, catConf) = effective
        if (catConf < low) {
            val msg = MessageGenerator.generate(
                category = cat,
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
                rawCategoryConfidence = catConf,
                rawSubcategoryConfidence = 0f,
                classificationMessage = msg
            )
        }
        val msg = MessageGenerator.generate(
            category = cat,
            subcategory = WasteMapping.UNKNOWN,
            catConfidence = catConf,
            subConfidence = 0f,
            topSubcategories = emptyList(),
            topCategories = prediction.topCategories,
            mode = ClassificationMode.CATEGORY_ONLY
        )
        return prediction.copy(
            category = cat,
            categoryConfidence = catConf,
            subcategory = WasteMapping.UNCERTAIN,
            subcategoryConfidence = 0f,
            classificationMessage = msg
        )
    }

    // ── Degraded: subclass-only (category model failed) ──────────────────────

    private fun arbitrateSubclassOnly(prediction: PredictionResult, low: Float = LOW_CONFIDENCE_THRESHOLD): PredictionResult {
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
                categoryConfidence = 0f,
                subcategoryConfidence = 0f,
                rawCategoryConfidence = 0f,
                rawSubcategoryConfidence = subConf,
                classificationMessage = msg
            )
        }
        if (subConf < low) {
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
                rawCategoryConfidence = 0f,
                rawSubcategoryConfidence = subConf,
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

    /**
     * Zero-zero policy: with no evidence from either model, confidences are ALWAYS
     * zeroed (never leak stale inputs) and the raw inputs are preserved in
     * [PredictionResult.rawCategoryConfidence]/[PredictionResult.rawSubcategoryConfidence]
     * for analytics — consistent with every other UNCERTAIN path.
     */
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
            categoryConfidence = 0f,
            subcategoryConfidence = 0f,
            rawCategoryConfidence = prediction.categoryConfidence.coerceIn(0f, 1f),
            rawSubcategoryConfidence = prediction.subcategoryConfidence.coerceIn(0f, 1f),
            classificationMessage = msg
        )
    }

    // ── Entropy estimation ───────────────────────────────────────────────────

    /**
     * Computes Shannon entropy (in bits) over the probability distribution implied by
     * [topPredictions]. Remaining probability mass is lumped into a single "rest" bucket
     * which OVERESTIMATES certainty (underestimates true entropy) — callers must treat
     * this as a LOWER bound on the true entropy: a low value here means "looks certain
     * under an optimistic assumption", never "proven certain". In particular a peaked
     * top-1 with large unaccounted mass still yields modest entropy; do NOT use this as
     * an upper bound or a standalone certainty gate.
     *
     * Empty (or fully non-finite/zero-mass) input carries NO information and returns the
     * maximum uncertainty [CATEGORY_ENTROPY_CEILING] — never `0f` (which would read as
     * "perfectly certain"). Non-finite and negative entries are ignored (NaN must not
     * defeat the confusion check the way `< NaN == false` defeats comparisons).
     */
    fun computeEntropy(topPredictions: List<Pair<String, Float>>): Float {
        val raw = topPredictions.mapNotNull { (_, conf) ->
            when {
                !conf.isFinite() || conf <= 0f -> null
                else -> conf.toDouble()
            }
        }
        if (raw.isEmpty()) return CATEGORY_ENTROPY_CEILING
        val sum = raw.sum()
        if (sum <= 0.0) return CATEGORY_ENTROPY_CEILING
        val normalized = if (sum > 1.0) raw.map { it / sum } else raw
        val accounted = normalized.sum()
        val rest = (1.0 - accounted).coerceAtLeast(0.0)
        val probs = normalized + listOfNotNull(rest.takeIf { it > 1e-6 })
        return probs.sumOf { p ->
            if (p > 0.0) -p * ln(p) / ln(2.0) else 0.0
        }.toFloat()
    }
}
