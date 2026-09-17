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
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
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
    @Volatile
    private var closed = false

    private val initMutex = Mutex()
    private val detectMutex = Mutex()

    private val inputSize = INPUT_SIZE

    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var pixelsBuffer: IntArray? = null
    private var rawOutputArray: FloatArray? = null
    private var letterboxBitmap: Bitmap? = null
    private var letterboxCanvas: android.graphics.Canvas? = null
    private val srcRect = android.graphics.Rect()
    private val dstRect = android.graphics.RectF()

    private var labels: List<String> = emptyList()

    private fun findModelAndLabels(): Pair<String, String?> {
        val assetList = try {
            appContext.assets.list("")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val candidates = listOf(
            "waste_yolo11n.tflite" to "waste_classes.txt",
            "waste_yolo11n.tflite" to "waste_yolo11n_classes.txt",
            "yolo11n.tflite" to "waste_classes.txt",
            "yolo11n.tflite" to "yolo11n_classes.txt",
            "yolov8n.tflite" to "waste_classes.txt",
            "yolov8n.tflite" to "yolov8n_classes.txt"
        )

        for ((mFile, lFile) in candidates) {
            if (mFile in assetList) {
                val resolvedLabels = if (lFile in assetList) lFile else null
                return mFile to resolvedLabels
            }
        }

        return "yolov8n.tflite" to null
    }

    suspend fun ensureInitialized() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized) return
            try {
                val (modelFilename, labelsFilename) = findModelAndLabels()
                Log.d(TAG, "Attempting to load YOLO model: $modelFilename")
                val model = FileUtil.loadMappedFile(appContext, modelFilename)
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                val interp = Interpreter(model, options)
                val isFloat = interp.getInputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32
                val bytesPerChannel = if (isFloat) 4 else 1
                inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * bytesPerChannel).apply {
                    order(ByteOrder.nativeOrder())
                }
                val outputShape = interp.getOutputTensor(0).shape()
                val outputSize = outputShape.fold(1) { acc, i -> acc * i }
                outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                pixelsBuffer = IntArray(INPUT_SIZE * INPUT_SIZE)
                rawOutputArray = FloatArray(outputSize)
                val lbBmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
                letterboxBitmap = lbBmp
                letterboxCanvas = android.graphics.Canvas(lbBmp)
                interpreter = interp

                val numClassesInModel = if (outputShape.size >= 3) {
                    minOf(outputShape[1], outputShape[2]) - 4
                } else 80

                labels = when {
                    numClassesInModel == 15 -> {
                        loadLabelsFromAssets("waste_classes.txt").ifEmpty {
                            if (labelsFilename != null) loadLabelsFromAssets(labelsFilename) else COCO_LABELS
                        }
                    }
                    labelsFilename != null -> loadLabelsFromAssets(labelsFilename).ifEmpty { COCO_LABELS }
                    else -> COCO_LABELS
                }

                isInitialized = true
                Log.d(TAG, "YOLO detector initialized: $modelFilename (${labels.size} classes, shape=${outputShape.contentToString()})")
            } catch (e: Exception) {
                Log.w(TAG, "YOLO model initialization error: ${e.message}")
                isInitialized = false
                throw ModelInitException("YOLO model could not be initialized from assets.", e)
            }
        }
    }

    private fun loadLabelsFromAssets(filename: String): List<String> {
        return try {
            val assetManager = appContext.assets
            BufferedReader(InputStreamReader(assetManager.open(filename))).use { reader ->
                reader.readLines().filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not load labels from $filename: ${e.message}")
            emptyList()
        }
    }

    private data class LetterboxCoords(
        val padX: Float,
        val padY: Float,
        val scaledW: Float,
        val scaledH: Float
    )

    private fun letterbox(src: Bitmap, targetSize: Int): LetterboxCoords {
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = minOf(targetSize / srcW, targetSize / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val padX = (targetSize - scaledW) / 2f
        val padY = (targetSize - scaledH) / 2f

        val canvas = letterboxCanvas
        if (canvas != null) {
            canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))
            srcRect.set(0, 0, src.width, src.height)
            dstRect.set(padX, padY, padX + scaledW, padY + scaledH)
            canvas.drawBitmap(src, srcRect, dstRect, null)
        }
        return LetterboxCoords(padX, padY, scaledW.toFloat(), scaledH.toFloat())
    }

    suspend fun detect(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        if (!isInitialized || bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            return@withContext emptyList()
        }

        detectMutex.withLock {
            if (closed) return@withContext emptyList()
            val interp = interpreter ?: return@withContext emptyList()
            val inBuf = inputBuffer ?: return@withContext emptyList()
            val outBuf = outputBuffer ?: return@withContext emptyList()
            val lbBitmap = letterboxBitmap ?: return@withContext emptyList()

            val lb = letterbox(bitmap, inputSize)
            try {
                inBuf.rewind()
                val isFloat = interp.getInputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32
                if (isFloat) {
                    bitmapToFloatByteBuffer(lbBitmap, inBuf)
                } else {
                    bitmapToByteByteBuffer(lbBitmap, inBuf)
                }

                outBuf.rewind()
                interp.run(inBuf, outBuf)

                outBuf.rewind()
                val outputShape = interp.getOutputTensor(0).shape()
                val rawOutput = rawOutputArray ?: FloatArray(outputShape.fold(1) { acc, i -> acc * i })
                outBuf.asFloatBuffer().get(rawOutput)

                parseDetections(rawOutput, outputShape, lb.padX, lb.padY, lb.scaledW, lb.scaledH)
            } catch (e: Exception) {
                Log.e(TAG, "YOLO detection failed", e)
                emptyList()
            } finally {
                if (closed) {
                    cleanupInternal()
                }
            }
        }
    }

    private fun bitmapToFloatByteBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        val pixels = pixelsBuffer ?: IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
    }

    private fun bitmapToByteByteBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        val pixels = pixelsBuffer ?: IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.put(((pixel shr 16) and 0xFF).toByte())
            buffer.put(((pixel shr 8) and 0xFF).toByte())
            buffer.put((pixel and 0xFF).toByte())
        }
    }

    private fun parseDetections(
        raw: FloatArray,
        outputShape: IntArray,
        padX: Float = 0f,
        padY: Float = 0f,
        scaledW: Float = inputSize.toFloat(),
        scaledH: Float = inputSize.toFloat()
    ): List<Detection> {
        val totalElements = raw.size
        if (totalElements == 0) return emptyList()

        val numDetections: Int
        val valuesPerDetection: Int
        val isChannelFirst: Boolean

        if (outputShape.size >= 3) {
            val dim1 = outputShape[1]
            val dim2 = outputShape[2]
            if (dim1 < dim2) {
                // Shape is [1, valuesPerDetection, numAnchors] e.g. [1, 84, 8400]
                valuesPerDetection = dim1
                numDetections = dim2
                isChannelFirst = true
            } else {
                // Shape is [1, numAnchors, valuesPerDetection] e.g. [1, 8400, 84]
                numDetections = dim1
                valuesPerDetection = dim2
                isChannelFirst = false
            }
        } else {
            // Fallback heuristics
            val fallbackClasses = labels.size
            valuesPerDetection = 4 + fallbackClasses
            if (totalElements % valuesPerDetection == 0) {
                numDetections = totalElements / valuesPerDetection
                isChannelFirst = false
            } else if (totalElements == (4 + fallbackClasses) * NUM_YOLO_ANCHORS) {
                numDetections = NUM_YOLO_ANCHORS
                isChannelFirst = true
            } else {
                Log.w(TAG, "Unexpected YOLO output shape: $totalElements elements")
                return emptyList()
            }
        }

        val numClassesToScore = maxOf(1, valuesPerDetection - 4)
        val effectiveClasses = minOf(labels.size, numClassesToScore)
        val detections = mutableListOf<Detection>()

        for (d in 0 until numDetections) {
            val cx: Float
            val cy: Float
            val w: Float
            val h: Float

            if (isChannelFirst) {
                cx = raw[0 * numDetections + d]
                cy = raw[1 * numDetections + d]
                w  = raw[2 * numDetections + d]
                h  = raw[3 * numDetections + d]
            } else {
                val offset = d * valuesPerDetection
                cx = raw[offset]
                cy = raw[offset + 1]
                w  = raw[offset + 2]
                h  = raw[offset + 3]
            }

            var maxScore = 0f
            var maxClassIdx = 0
            for (c in 0 until effectiveClasses) {
                val rawScore = if (isChannelFirst) {
                    raw[(4 + c) * numDetections + d]
                } else {
                    raw[d * valuesPerDetection + 4 + c]
                }
                if (rawScore > maxScore) {
                    maxScore = rawScore
                    maxClassIdx = c
                }
            }

            // Apply sigmoid if output appears to be unnormalized logits
            val score = if (maxScore > 1.0f || maxScore < 0.0f) {
                1.0f / (1.0f + kotlin.math.exp(-maxScore))
            } else {
                maxScore
            }

            if (score < CONFIDENCE_THRESHOLD) continue

            // Normalize coordinate scale if model output was 0..1 normalized instead of pixels
            val (absCx, absCy, absW, absH) = if (cx <= 1.0f && cy <= 1.0f && w <= 1.0f && h <= 1.0f) {
                floatArrayOf(cx * inputSize, cy * inputSize, w * inputSize, h * inputSize)
            } else {
                floatArrayOf(cx, cy, w, h)
            }

            val normW = absW / scaledW
            val normH = absH / scaledH
            if (normW * normH < MIN_BOX_AREA) continue

            val left = ((absCx - absW / 2f - padX) / scaledW).coerceIn(0f, 1f)
            val top = ((absCy - absH / 2f - padY) / scaledH).coerceIn(0f, 1f)
            val right = ((absCx + absW / 2f - padX) / scaledW).coerceIn(0f, 1f)
            val bottom = ((absCy + absH / 2f - padY) / scaledH).coerceIn(0f, 1f)

            if (right <= left || bottom <= top) continue

            detections.add(
                Detection(
                    label = labels.getOrElse(maxClassIdx) { "waste_item" },
                    confidence = score,
                    boundingBox = RectF(left, top, right, bottom),
                    classIndex = maxClassIdx
                )
            )
        }

        return nonMaxSuppression(detections)
            .take(MAX_DISPLAYED_DETECTIONS)
    }

    private fun nonMaxSuppression(detections: List<Detection>): List<Detection> {
        return detections.groupBy { it.classIndex }
            .flatMap { (_, classDets) -> nmsPerClass(classDets) }
            .sortedByDescending { it.confidence }
    }

    private fun nmsPerClass(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val suppressed = BooleanArray(sorted.size)
        val result = ArrayList<Detection>(minOf(sorted.size, MAX_DISPLAYED_DETECTIONS))

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val best = sorted[i]
            result.add(best)
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(best.boundingBox, sorted[j].boundingBox) > IOU_THRESHOLD) {
                    suppressed[j] = true
                }
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

    private fun cleanupInternal() {
        runCatching { interpreter?.close() }
        interpreter = null
        inputBuffer = null
        outputBuffer = null
        letterboxBitmap?.recycle()
        letterboxBitmap = null
        letterboxCanvas = null
        pixelsBuffer = null
        rawOutputArray = null
        isInitialized = false
    }

    override fun close() {
        closed = true
        isInitialized = false
        if (detectMutex.tryLock()) {
            try {
                cleanupInternal()
            } finally {
                detectMutex.unlock()
            }
        }
        // If detectMutex is currently held, the in-flight detect()
        // will call cleanupInternal() in its finally block once inference finishes.
    }

    companion object {
        private const val TAG = "YoloDetector"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.45f
        private const val MIN_BOX_AREA = 0.003f
        private const val MAX_DISPLAYED_DETECTIONS = 15
        private const val NUM_YOLO_ANCHORS = 8400

        // Built-in COCO 80-class fallback (used when no label file is present)
        private val COCO_LABELS = listOf(
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
    }
}
