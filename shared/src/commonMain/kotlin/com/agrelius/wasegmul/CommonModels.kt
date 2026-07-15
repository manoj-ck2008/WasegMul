package com.agrelius.wasegmul

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
    val timestamp: Long = 0L
)

data class PredictionResult(
    val category: String,
    val categoryConfidence: Float,
    val subcategory: String,
    val subcategoryConfidence: Float,
    val topSubcategories: List<Pair<String, Float>> = emptyList()
)

data class ClassificationResult(
    val category: String,
    val subclass: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>>,
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String
)

data class InternalResult(
    val label: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>> = emptyList()
)

data class WasteInfo(
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String
)
