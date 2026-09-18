package com.agrelius.wasegmul

/**
 * Cross-platform domain models shared between the Android and iOS targets.
 *
 * These mirror the Room-persisted entity (see `data/WasteRecord.kt` in the Android module)
 * and the in-memory prediction payloads produced by the ML pipeline.
 */

/** A persisted classification record. */
data class WasteRecord(
    val id: Long = 0,
    val category: String,
    val subclass: String,
    val confidence: Float,
    val estimatedWeight: Double = 0.05,
    val featureVector: String? = null,
    val imagePath: String? = null,
    val feedback: String? = null,
    val correctedSubclass: String? = null,
    /** Serialised "label|confidence;label|confidence" list of top subclass predictions. */
    val topPredictions: String? = null,
    // 0 = unset (legacy). Callers should set System.currentTimeMillis() on insert.
    val timestamp: Long = 0L
) {
    init {
        require(confidence.isFinite() && confidence in 0f..1f) { "confidence must be in 0..1" }
        require(estimatedWeight.isFinite() && estimatedWeight >= 0.0) { "weight must be finite >= 0" }
    }
}

/** Raw merged output of the category + subclass classifiers before arbitration. */
data class PredictionResult(
    val category: String,
    val categoryConfidence: Float,
    val subcategory: String,
    val subcategoryConfidence: Float,
    val topSubcategories: List<Pair<String, Float>> = emptyList(),
    /** Top category predictions (label → confidence) for uncertainty estimation. */
    val topCategories: List<Pair<String, Float>> = emptyList(),
    /** Dynamic user-facing message explaining the classification outcome. */
    val classificationMessage: String = ""
) {
    // Canonical domain aliases matching CONTEXT.md
    val subclass: String get() = subcategory
    val subclassConfidence: Float get() = subcategoryConfidence
    val topSubclasses: List<Pair<String, Float>> get() = topSubcategories

    init {
        require(categoryConfidence.isFinite()) { "categoryConfidence must be finite" }
        require(subcategoryConfidence.isFinite()) { "subcategoryConfidence must be finite" }
    }
}

/**
 * Product packaging evidence obtained from barcode lookup.
 */
data class BarcodeEvidence(
    val productName: String,
    val category: String,
    val subclass: String,
    val isComplete: Boolean = true
)

/** UI-facing classification payload (post-arbitration + knowledge-base lookup). */
data class ClassificationResult(
    val category: String,
    val subclass: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>>,
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String,
    /** Dynamic message explaining the classification outcome to the user. */
    val classificationMessage: String = ""
) {
    init {
        require(confidence.isFinite()) { "confidence must be finite" }
    }
}

/** Raw classifier output bridging the Android TFLite layer to shared types. */
data class InternalResult(
    val label: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>> = emptyList()
) {
    init {
        require(confidence.isFinite()) { "confidence must be finite" }
    }
}

/** Knowledge-base payload returned for a (category, subclass) pair. */
data class WasteInfo(
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String
)

