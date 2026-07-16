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
    val timestamp: Long = 0L
)

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
)

/** Raw classifier output bridging the Android TFLite layer to shared types. */
data class InternalResult(
    val label: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>> = emptyList()
)

/** Knowledge-base payload returned for a (category, subclass) pair. */
data class WasteInfo(
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String
)
