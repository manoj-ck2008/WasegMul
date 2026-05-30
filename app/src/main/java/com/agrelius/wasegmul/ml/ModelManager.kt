package com.agrelius.wasegmul.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.agrelius.wasegmul.data.PredictionResult
import com.agrelius.wasegmul.ml.classifiers.CategoryClassifier
import com.agrelius.wasegmul.ml.classifiers.SubclassClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ModelManager(context: Context) {

    private var categoryClassifier: CategoryClassifier? = null
    private var subclassClassifier: SubclassClassifier? = null

    init {
        try {
            categoryClassifier = CategoryClassifier(context)
            subclassClassifier = SubclassClassifier(context)
            Log.d(TAG, "Classifiers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize classifiers", e)
        }
    }

    suspend fun classify(bitmap: Bitmap): PredictionResult? = withContext(Dispatchers.Default) {
        try {
            val catResult = categoryClassifier?.classify(bitmap) ?: return@withContext null
            val subResult = subclassClassifier?.classify(bitmap) ?: return@withContext null

            PredictionResult(
                category = catResult.label,
                categoryConfidence = catResult.confidence,
                subcategory = subResult.label,
                subcategoryConfidence = subResult.confidence,
                topSubcategories = subResult.topPredictions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Classification error", e)
            null
        }
    }

    fun close() {
        categoryClassifier?.close()
        subclassClassifier?.close()
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}

data class InternalResult(
    val label: String,
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>> = emptyList()
)
