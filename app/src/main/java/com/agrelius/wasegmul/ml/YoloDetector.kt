package com.agrelius.wasegmul.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val classIndex: Int
)

class YoloDetector(context: Context) : Closeable {

    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null

    @Volatile
    private var isInitialized = false

    private val initMutex = Mutex()
    private val detectMutex = Mutex()

    private val inputSize = INPUT_SIZE

    private val cocoLabels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train",
        "truck", "boat", "traffic light", "fire hydrant", "stop sign",
        "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep",
        "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
        "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard",
        "sports ball", "kite", "baseball bat", "baseball glove", "skateboard",
        "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork",
        "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv",
        "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave",
        "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
        "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    suspend fun ensureInitialized() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized) return
            try {
                val model = FileUtil.loadMappedFile(appContext, MODEL_FILENAME)
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                interpreter = Interpreter(model, options)
                isInitialized = true
                Log.d(TAG, "YOLO detector initialized — ${MODEL_FILENAME}")
            } catch (e: Exception) {
                Log.w(TAG, "YOLO model not available (${MODEL_FILENAME}): ${e.message}")
                isInitialized = false
                throw ModelInitException("YOLO model not found. Place ${MODEL_FILENAME} in assets/.", e)
            }
        }
    }

    suspend fun detect(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        if (!isInitialized || bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            return@withContext emptyList()
        }

        detectMutex.withLock {
            val interp = interpreter ?: return@withContext emptyList()

            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            try {
                val inputBuffer = bitmapToByteBuffer(resized)

                val outputShape = interp.getOutputTensor(0).shape()
                val outputSize = outputShape.fold(1) { acc, i -> acc * i }
                val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4)
                outputBuffer.order(ByteOrder.nativeOrder())

                interp.run(inputBuffer, outputBuffer)

                outputBuffer.rewind()
                val rawOutput = FloatArray(outputSize)
                outputBuffer.asFloatBuffer().get(rawOutput)

                parseDetections(rawOutput, outputShape)
            } catch (e: Exception) {
                Log.e(TAG, "YOLO detection failed", e)
                emptyList()
            } finally {
                if (resized !== bitmap) resized.recycle()
            }
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        return buffer
    }

    private fun parseDetections(raw: FloatArray, outputShape: IntArray): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numClasses = cocoLabels.size
        val valuesPerDetection = 4 + numClasses

        val totalElements = raw.size
        val numDetections: Int
        val transposed: FloatArray

        if (totalElements == valuesPerDetection && raw.size == numClasses + 4) {
            numDetections = 1
            transposed = raw
        } else if (totalElements % valuesPerDetection == 0) {
            numDetections = totalElements / valuesPerDetection
            transposed = raw
        } else if (totalElements == (4 + numClasses) * NUM_YOLO_ANCHORS) {
            numDetections = NUM_YOLO_ANCHORS
            transposed = FloatArray(totalElements)
            val srcRows = 4 + numClasses
            for (d in 0 until NUM_YOLO_ANCHORS) {
                for (c in 0 until srcRows) {
                    transposed[d * srcRows + c] = raw[c * NUM_YOLO_ANCHORS + d]
                }
            }
        } else {
            Log.w(TAG, "Unexpected YOLO output shape: $totalElements elements")
            return emptyList()
        }

        for (d in 0 until numDetections) {
            val offset = d * valuesPerDetection
            val cx = transposed[offset]
            val cy = transposed[offset + 1]
            val w = transposed[offset + 2]
            val h = transposed[offset + 3]

            var maxScore = 0f
            var maxClassIdx = 0
            for (c in 0 until numClasses) {
                val score = transposed[offset + 4 + c]
                if (score > maxScore) {
                    maxScore = score
                    maxClassIdx = c
                }
            }

            if (maxScore < CONFIDENCE_THRESHOLD) continue

            val left = max(0f, cx - w / 2f) / inputSize
            val top = max(0f, cy - h / 2f) / inputSize
            val right = min(inputSize.toFloat(), cx + w / 2f) / inputSize
            val bottom = min(inputSize.toFloat(), cy + h / 2f) / inputSize

            if (right <= left || bottom <= top) continue

            detections.add(
                Detection(
                    label = cocoLabels.getOrElse(maxClassIdx) { "unknown" },
                    confidence = maxScore,
                    boundingBox = RectF(left, top, right, bottom),
                    classIndex = maxClassIdx
                )
            )
        }

        return nonMaxSuppression(detections.sortedByDescending { it.confidence })
    }

    private fun nonMaxSuppression(detections: List<Detection>): List<Detection> {
        val result = mutableListOf<Detection>()
        val remaining = detections.toMutableList()

        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            result.add(best)
            remaining.removeAll { other ->
                iou(best.boundingBox, other.boundingBox) > IOU_THRESHOLD
            }
        }
        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return if (aArea + bArea - interArea > 0f) interArea / (aArea + bArea - interArea) else 0f
    }

    override fun close() {
        isInitialized = false
        runCatching { interpreter?.close() }
        interpreter = null
    }

    companion object {
        private const val TAG = "YoloDetector"
        private const val MODEL_FILENAME = "yolov8n.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.5f
        private const val NUM_YOLO_ANCHORS = 8400
    }
}
