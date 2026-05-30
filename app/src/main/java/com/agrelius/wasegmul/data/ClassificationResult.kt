package com.agrelius.wasegmul.data

data class ClassificationResult(
    val category: String,
    val subclass: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>>,
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String
)
