package com.agrelius.wasegmul.ml.classifiers

import android.content.Context

/**
 * Wraps the EfficientNet category model and maps its softmax output to one of the four
 * category labels (E-Waste, Organic, Recyclable, Trash).
 *
 * Returns the top-[TOP_K] predictions so the arbitrator can assess category-model
 * uncertainty (e.g. when two categories have close probabilities).
 *
 * Inference runs with [NUM_THREADS] CPU threads; the interpreter is closed via [close].
 */
class CategoryClassifier(
    context: Context,
    modelFilename: String = MODEL_FILENAME,
    labelsFilename: String = LABELS_FILENAME
) : TfliteClassifier(
    context = context,
    modelFilename = modelFilename,
    labelsFilename = labelsFilename,
    topK = TOP_K,
    numThreads = NUM_THREADS,
    tag = TAG
) {
    companion object {
        private const val TAG = "CategoryClassifier"
        private const val MODEL_FILENAME = "category_model_finetuned.tflite"
        private const val LABELS_FILENAME = "category_classes.txt"
        private const val NUM_THREADS = 2
        // 4 classes total: return all so entropy is exact, not lumped.
        private const val TOP_K = 4
    }
}

