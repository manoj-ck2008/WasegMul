package com.agrelius.wasegmul.ml.classifiers

import android.content.Context

/**
 * Wraps the EfficientNet subclass model and returns the top-[TOP_K] subclass predictions ranked
 * by softmax confidence. Output is consumed by [com.agrelius.wasegmul.MLArbitrator].
 *
 * Inference runs with [NUM_THREADS] CPU threads; the interpreter is closed via [close].
 */
class SubclassClassifier(
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
        private const val TAG = "SubclassClassifier"
        private const val MODEL_FILENAME = "subclass_model_finetuned.tflite"
        private const val LABELS_FILENAME = "subclass_classes.txt"
        private const val NUM_THREADS = 2
        private const val TOP_K = 5
    }
}

