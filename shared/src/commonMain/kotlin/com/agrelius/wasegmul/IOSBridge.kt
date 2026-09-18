package com.agrelius.wasegmul

/**
 * Bridge for iOS to access shared logic easily.
 * Exposes flattened/Obj-C-friendly APIs (no Kotlin Pair in signatures).
 *
 * Conventions for every function here:
 * - Parallel label/confidence arrays MUST have equal size — the `*Strict`/flat entry
 *   points throw [IllegalArgumentException] on mismatch (silent `zip` truncation hid
 *   caller bugs). The legacy lenient overloads are kept only where noted, and documented.
 * - `mode` strings are [ClassificationMode] names. [generateMessage] keeps its legacy
 *   silent-fallback contract (documented); new callers must use [generateMessageStrict].
 * - All confidences are calibrated probabilities in `0..1`; weights in flat Eco Impact
 *   overloads are in **kg**.
 */
object IOSBridge {
    /**
     * Disposal/environmental knowledge for a (category, subclass) pair.
     * See [WasteKnowledgeBase.getInfo].
     */
    fun getWasteKnowledge(category: String, subclass: String): WasteInfo {
        return WasteKnowledgeBase.getInfo(category, subclass)
    }

    /**
     * Arbitrates a fully-formed [PredictionResult] (Kotlin-side construction).
     * Swift callers without Pair support should use [arbitrateMLFlatStrict].
     */
    fun arbitrateML(prediction: PredictionResult): PredictionResult {
        return MLArbitrator.arbitrate(prediction)
    }

    /**
     * Flattened arbitration overload for Swift/Objective-C without Kotlin Pair.
     *
     * @param lowThreshold user confidence slider value (0.10..0.95, coerced); NaN falls
     *   back to [MLArbitrator.LOW_CONFIDENCE_THRESHOLD].
     * @throws IllegalArgumentException when any parallel label/confidence arrays differ
     *   in size (fail-fast: truncation would silently corrupt evidence).
     */
    fun arbitrateMLFlat(
        category: String,
        categoryConfidence: Float,
        subclass: String,
        subclassConfidence: Float,
        topSubclassLabels: List<String>,
        topSubclassConfidences: List<Float>,
        topCategoryLabels: List<String> = emptyList(),
        topCategoryConfidences: List<Float> = emptyList(),
        lowThreshold: Float = MLArbitrator.LOW_CONFIDENCE_THRESHOLD
    ): PredictionResult {
        require(topSubclassLabels.size == topSubclassConfidences.size) {
            "topSubclassLabels (${topSubclassLabels.size}) and topSubclassConfidences (${topSubclassConfidences.size}) must have equal size"
        }
        require(topCategoryLabels.size == topCategoryConfidences.size) {
            "topCategoryLabels (${topCategoryLabels.size}) and topCategoryConfidences (${topCategoryConfidences.size}) must have equal size"
        }
        val topSubs = topSubclassLabels.zip(topSubclassConfidences) { l, c -> l to c }
        val topCats = topCategoryLabels.zip(topCategoryConfidences) { l, c -> l to c }
        val input = PredictionResult(
            category = category,
            categoryConfidence = categoryConfidence,
            subcategory = subclass,
            subcategoryConfidence = subclassConfidence,
            topSubcategories = topSubs,
            topCategories = topCats
        )
        return MLArbitrator.arbitrate(input, lowThreshold)
    }

    /**
     * Flattened barcode arbitration for Swift: visual evidence (nullable/empty when the
     * camera path was skipped) plus barcode ground truth.
     * See [MLArbitrator.arbitrateWithBarcode] for the validation/merge contract.
     *
     * @param productName barcode product name (blank renders neutrally as "this product";
     *   the API never invents names).
     * @param barcodeCategory validated against the taxonomy; unknown values fall back to
     *   visual-only arbitration or honest UNKNOWN (never blind-trusted).
     * @param isComplete true only for fully-resolved packaging metadata (fail-closed default false).
     */
    fun arbitrateMLBarcode(
        category: String,
        categoryConfidence: Float,
        subclass: String,
        subclassConfidence: Float,
        topSubclassLabels: List<String>,
        topSubclassConfidences: List<Float>,
        topCategoryLabels: List<String> = emptyList(),
        topCategoryConfidences: List<Float> = emptyList(),
        productName: String,
        barcodeCategory: String,
        barcodeSubclass: String,
        isComplete: Boolean = false
    ): PredictionResult {
        require(topSubclassLabels.size == topSubclassConfidences.size) {
            "topSubclassLabels and topSubclassConfidences must have equal size"
        }
        require(topCategoryLabels.size == topCategoryConfidences.size) {
            "topCategoryLabels and topCategoryConfidences must have equal size"
        }
        val hasVisual = topSubclassLabels.isNotEmpty() || topCategoryLabels.isNotEmpty()
        val visual = if (hasVisual || category.isNotBlank() || subclass.isNotBlank()) {
            PredictionResult(
                category = category,
                categoryConfidence = categoryConfidence,
                subcategory = subclass,
                subcategoryConfidence = subclassConfidence,
                topSubcategories = topSubclassLabels.zip(topSubclassConfidences) { l, c -> l to c },
                topCategories = topCategoryLabels.zip(topCategoryConfidences) { l, c -> l to c }
            )
        } else {
            null
        }
        return MLArbitrator.arbitrateWithBarcode(
            visual,
            BarcodeEvidence(
                productName = productName,
                category = barcodeCategory,
                subclass = barcodeSubclass,
                isComplete = isComplete
            )
        )
    }

    /** Canonical category for a subclass label ([WasteMapping.getCategory]). */
    fun getCategory(subclass: String): String = WasteMapping.getCategory(subclass)

    /** Estimated unit weight in **kg** ([WasteMapping.getWeight]). */
    fun getWeight(subclass: String): Double = WasteMapping.getWeight(subclass)

    /** Hazardous-routing flag ([WasteMapping.isHazardous]). */
    fun isHazardous(subclass: String): Boolean = WasteMapping.isHazardous(subclass)

    /**
     * Encodes parallel label/confidence arrays via [PredictionCodec].
     * @throws IllegalArgumentException on size mismatch.
     */
    fun encodePredictions(labels: List<String>, confidences: List<Float>): String {
        require(labels.size == confidences.size) {
            "labels (${labels.size}) and confidences (${confidences.size}) must have equal size"
        }
        val pairs = labels.zip(confidences) { l, c -> l to c }
        return PredictionCodec.encode(pairs)
    }

    /**
     * Legacy pipe-joined decode (`"label|conf"` strings). Kept for existing Swift callers;
     * new callers should prefer [decodePredictionLabels]/[decodePredictionConfidences]/
     * [decodePredictionsMap], which need no re-parsing (and avoid decimal-separator fragility).
     */
    fun decodePredictions(raw: String?): List<String> {
        // Flattened for Obj-C: "label|conf;..." strings
        return PredictionCodec.decode(raw).map { "${it.first}|${it.second}" }
    }

    /** Swift-friendly decoded labels (no re-parsing needed). */
    fun decodePredictionLabels(raw: String?): List<String> =
        PredictionCodec.decode(raw).map { it.first }

    /** Swift-friendly decoded confidences in `0..1`, parallel to [decodePredictionLabels]. */
    fun decodePredictionConfidences(raw: String?): List<Float> =
        PredictionCodec.decode(raw).map { it.second }

    /** Swift-friendly decoded dictionary (label → confidence in `0..1`). */
    fun decodePredictionsMap(raw: String?): Map<String, Float> =
        PredictionCodec.decode(raw).toMap()

    /**
     * Convenience alias for the total weighed kilograms. Delegates to [calculateEcoImpact]
     * (single computation — no duplicated logic). See [EcoImpactMetrics] for what
     * "total" includes (credited weight PLUS plain Trash landfill weight).
     */
    fun calculateImpactTotalWeight(records: List<WasteRecord>): Double {
        return EcoImpactCalculator.calculate(records).totalWeightKg
    }

    /** Full Eco Impact computation for Kotlin-held records. */
    fun calculateEcoImpact(records: List<WasteRecord>): EcoImpactMetrics {
        return EcoImpactCalculator.calculate(records)
    }

    /**
     * Flat-array Eco Impact for Swift (parallel arrays; see [EcoImpactCalculator.calculateFlat]
     * for the truncation contract). Weights in **kg**, confidences in `0..1`.
     */
    fun calculateEcoImpactFlat(
        categories: List<String>,
        subclasses: List<String>,
        weightsKg: List<Double>,
        confidences: List<Float>
    ): EcoImpactMetrics =
        EcoImpactCalculator.calculateFlat(categories, subclasses, weightsKg, confidences)

    /**
     * Strict flat-array Eco Impact: equal-size arrays required (throws otherwise).
     * Weights in **kg**, confidences in `0..1`.
     */
    fun calculateEcoImpactFlatStrict(
        categories: List<String>,
        subclasses: List<String>,
        weightsKg: List<Double>,
        confidences: List<Float>,
        feedbacks: List<String?> = emptyList(),
        correctedSubclasses: List<String?> = emptyList()
    ): EcoImpactMetrics =
        EcoImpactCalculator.calculateFlatStrict(
            categories, subclasses, weightsKg, confidences, feedbacks, correctedSubclasses
        )

    /**
     * Legacy message entry point. Kept for existing Swift callers: an unrecognised [mode]
     * silently falls back to BOTH_UNCERTAIN ("typo → cannot identify" behaviour). New
     * callers must use [generateMessageStrict]. Both category AND subclass top evidence
     * are forwarded (subclass-only before was dropped).
     */
    fun generateMessage(
        category: String,
        subcategory: String,
        catConfidence: Float,
        subConfidence: Float,
        mode: String,
        topSubclassLabels: List<String> = emptyList(),
        topSubclassConfidences: List<Float> = emptyList(),
        topCategoryLabels: List<String> = emptyList(),
        topCategoryConfidences: List<Float> = emptyList()
    ): String {
        val m = try { ClassificationMode.valueOf(mode) } catch (_: Exception) { ClassificationMode.BOTH_UNCERTAIN }
        require(topSubclassLabels.size == topSubclassConfidences.size) {
            "topSubclassLabels and topSubclassConfidences must have equal size"
        }
        require(topCategoryLabels.size == topCategoryConfidences.size) {
            "topCategoryLabels and topCategoryConfidences must have equal size"
        }
        val topSubs = topSubclassLabels.zip(topSubclassConfidences) { l, c -> l to c }
        val topCats = topCategoryLabels.zip(topCategoryConfidences) { l, c -> l to c }
        return MessageGenerator.generate(category, subcategory, catConfidence, subConfidence, topSubs, topCats, m)
    }

    /**
     * Strict message entry point: unknown [mode] throws [IllegalArgumentException] instead
     * of silently degrading to BOTH_UNCERTAIN.
     */
    fun generateMessageStrict(
        category: String,
        subcategory: String,
        catConfidence: Float,
        subConfidence: Float,
        mode: String,
        topSubclassLabels: List<String> = emptyList(),
        topSubclassConfidences: List<Float> = emptyList(),
        topCategoryLabels: List<String> = emptyList(),
        topCategoryConfidences: List<Float> = emptyList()
    ): String {
        val m = try {
            ClassificationMode.valueOf(mode)
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "Unknown ClassificationMode '$mode'. Valid: " +
                    ClassificationMode.values().joinToString(",")
            )
        }
        return generateMessage(
            category, subcategory, catConfidence, subConfidence, m.name,
            topSubclassLabels, topSubclassConfidences, topCategoryLabels, topCategoryConfidences
        )
    }

    /**
     * Barcode-grounded message for Swift. [productName] is required (non-blank) — the API
     * never synthesises product names; Swift must render its own localized fallback.
     */
    fun generateBarcodeMessage(
        productName: String,
        category: String,
        subclass: String,
        mode: String
    ): String {
        val m = try {
            ClassificationMode.valueOf(mode)
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "Unknown ClassificationMode '$mode'. Valid: " +
                    ClassificationMode.values().joinToString(",")
            )
        }
        return MessageGenerator.generateBarcode(productName, category, subclass, m)
    }
}
