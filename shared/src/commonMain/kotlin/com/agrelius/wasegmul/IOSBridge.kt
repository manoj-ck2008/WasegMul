package com.agrelius.wasegmul

/**
 * Bridge for iOS to access shared logic easily.
 * Exposes flattened/Obj-C-friendly APIs (no Kotlin Pair in signatures).
 */
object IOSBridge {
    fun getWasteKnowledge(category: String, subclass: String): WasteInfo {
        return WasteKnowledgeBase.getInfo(category, subclass)
    }

    fun arbitrateML(prediction: PredictionResult): PredictionResult {
        return MLArbitrator.arbitrate(prediction)
    }

    /** Flattened arbitration overload for Swift/Objective-C without Kotlin Pair. */
    fun arbitrateMLFlat(
        category: String,
        categoryConfidence: Float,
        subclass: String,
        subclassConfidence: Float,
        topSubclassLabels: List<String>,
        topSubclassConfidences: List<Float>,
        topCategoryLabels: List<String> = emptyList(),
        topCategoryConfidences: List<Float> = emptyList()
    ): PredictionResult {
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
        return MLArbitrator.arbitrate(input)
    }

    fun getCategory(subclass: String): String = WasteMapping.getCategory(subclass)

    fun getWeight(subclass: String): Double = WasteMapping.getWeight(subclass)

    fun isHazardous(subclass: String): Boolean = WasteMapping.isHazardous(subclass)

    fun encodePredictions(labels: List<String>, confidences: List<Float>): String {
        val count = minOf(labels.size, confidences.size)
        val pairs = (0 until count).map { labels[it] to confidences[it] }
        return PredictionCodec.encode(pairs)
    }

    fun decodePredictions(raw: String?): List<String> {
        // Flattened for Obj-C: "label|conf;..." strings
        return PredictionCodec.decode(raw).map { "${it.first}|${it.second}" }
    }

    fun calculateImpactTotalWeight(records: List<WasteRecord>): Double {
        return EcoImpactCalculator.calculate(records).totalWeightKg
    }

    fun calculateEcoImpact(records: List<WasteRecord>): EcoImpactMetrics {
        return EcoImpactCalculator.calculate(records)
    }

    fun generateMessage(
        category: String,
        subcategory: String,
        catConfidence: Float,
        subConfidence: Float,
        mode: String,
        topSubclassLabels: List<String> = emptyList(),
        topSubclassConfidences: List<Float> = emptyList()
    ): String {
        val m = try { ClassificationMode.valueOf(mode) } catch (_: Exception) { ClassificationMode.BOTH_UNCERTAIN }
        val topSubs = topSubclassLabels.zip(topSubclassConfidences) { l, c -> l to c }
        return MessageGenerator.generate(category, subcategory, catConfidence, subConfidence, topSubs, emptyList(), m)
    }
}
