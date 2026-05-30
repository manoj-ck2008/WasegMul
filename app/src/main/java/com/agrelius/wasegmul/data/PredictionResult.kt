package com.agrelius.wasegmul.data

data class PredictionResult(
    val category: String,
    val categoryConfidence: Float,
    val subcategory: String,
    val subcategoryConfidence: Float,
    val topSubcategories: List<Pair<String, Float>> = emptyList()
)
