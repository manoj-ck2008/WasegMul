package com.agrelius.wasegmul.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
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

/**
 * Outcome of a YOLO inference pass. Prefer [detectResult]: unlike [detect] it
 * distinguishes "no objects above threshold" ([Success] with an empty list) from
 * an inference failure ([Failure]), instead of collapsing both to `emptyList()`.
 */
sealed interface DetectionOutcome {
    data class Success(val detections: List<Detection>) : DetectionOutcome
    data class Failure(val message: String, val cause: Throwable? = null) : DetectionOutcome
}

/**
 * YOLO object detector (v8/v11, TFLite) for locating waste items in camera frames.
 *
 * ### Rotation contract (§3.25/§3.43)
 * The detector does NOT read EXIF or `ImageProxy` metadata: **callers MUST pass
 * [detect]'s [rotationDegrees] (from `imageProxy.imageInfo.rotationDegrees`) or
 * pre-rotate the bitmap to upright**. An unrotated portrait frame is letterboxed
 * sideways and misclassified. The CameraX analyzer in `YoloScreen` currently passes
 * `toBitmap()` upright RGBA frames; if that pipeline ever switches to YUV without
 * rotation, pass the degrees here.
 *
 * ### Coordinate contract
 * YOLOv8/v11 TFLite exports emit CENTER-XY-WH in **pixel space** (`0..640`).
 * [coordinatesNormalized] selects the interpretation explicitly from model metadata
 * (pass `true` only for exports documented as `0..1`-normalized) — never the old
 * `cx <= 1` value heuristic, which mis-scaled small near-origin pixel boxes ×640.
 *
 * ### Threading (§2.8)
 * A SINGLE [stateMutex] guards init + detect + close. `close()` is synchronous
 * ([Closeable]) so it uses `tryLock`; an in-flight `detect()` performs the cleanup
 * in its `finally` block when close loses the race — no native SIGSEGV from
 * use-after-close.
 *
 * Must be called off the Main thread for `detect` (it already confines to
 * `Dispatchers.Default`); callers must additionally close the `ImageProxy` AFTER
 * dispatch and recycle any `copy()` they made downstream (§3.43 — enforced at the
 * `YoloScreen` call site, documented here as the contract).
 */
class YoloDetector(
    context: Context,
    private val coordinatesNormalized: Boolean = false
) : Closeable {

    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null

    @Volatile
    private var isInitialized = false
    @Volatile
    private var closed = false

    // §2.8: ONE mutex guards init + detect + close (previously split initMutex /
    // detectMutex let close() race inference → native SIGSEGV).
    private val stateMutex = Mutex()

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

    /** User-tunable confidence threshold (Settings slider). Defaults to 0.45. */
    @Volatile
    var confidenceThreshold: Float = CONFIDENCE_THRESHOLD
        private set

    fun setConfidenceThreshold(value: Float) {
        confidenceThreshold = value.coerceIn(0.10f, 0.95f)
    }

    private fun findModelAndLabels(): Pair<String, String?> {
        val assetList = try {
            appContext.assets.list("")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Model candidates in preference order. The repeated `waste_yolo11n.tflite`
        // entries are INTENTIONAL (label-fallback chain, not duplicates): the first
        // hit wins. Pair-level exact duplicates are dropped defensively below.
        val candidates = listOf(
            "waste_yolo11n.tflite" to "waste_classes.txt",
            "waste_yolo11n.tflite" to "waste_yolo11n_classes.txt",
            "yolo11n.tflite" to "waste_classes.txt",
            "yolo11n.tflite" to "yolo11n_classes.txt",
            "yolov8n.tflite" to "waste_classes.txt",
            "yolov8n.tflite" to "yolov8n_classes.txt"
        ).distinct()

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
        stateMutex.withLock {
            if (isInitialized) return
            try {
                val (modelFilename, labelsFilename) = findModelAndLabels()
                Log.d(TAG, "Attempting to load YOLO model: $modelFilename")
                val model = FileUtil.loadMappedFile(appContext, modelFilename)
                // Device-aware threads (§3.34): leave headroom for camera + UI instead
                // of a fixed 4 (low-end 4-core devices starved the preview pipeline).
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                val options = Interpreter.Options().apply {
                    setNumThreads(threads)
                }
                val interp = Interpreter(model, options)
                val inputDtype = interp.getInputTensor(0).dataType()
                val isFloat = inputDtype == DataType.FLOAT32
                val bytesPerChannel = if (isFloat) 4 else 1
                inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * bytesPerChannel).apply {
                    order(ByteOrder.nativeOrder())
                }
                val outputShape = interp.getOutputTensor(0).shape()
                val outputSize = outputShape.fold(1) { acc, i -> acc * i }
                // Output buffer sized by dtype: quantized UINT8 outputs are 1 byte/elem
                // (the old code always allocated 4 bytes/elem and misread them as float).
                val outDtype = interp.getOutputTensor(0).dataType()
                val bytesPerOutput = if (outDtype == DataType.FLOAT32) 4 else 1
                outputBuffer = ByteBuffer.allocateDirect(outputSize * bytesPerOutput).apply {
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

                // Production fix: never pair an 80-class COCO model with 15 waste labels.
                // A stock yolov8n.tflite ([1,84,8400]) MUST use the 80 COCO labels so
                // COCO 0 (person) is not misreported as waste label 0 (battery).
                // Only a dedicated 15-class waste model may use waste_classes.txt.
                val fileLabels = if (labelsFilename != null) loadLabelsFromAssets(labelsFilename) else emptyList()
                val wasteLabels = loadLabelsFromAssets("waste_classes.txt")
                labels = when {
                    numClassesInModel == 15 && wasteLabels.size == 15 -> wasteLabels
                    numClassesInModel == 15 && fileLabels.size == 15 -> fileLabels
                    numClassesInModel == 80 -> COCO_LABELS
                    fileLabels.size == numClassesInModel && fileLabels.isNotEmpty() -> fileLabels
                    wasteLabels.size == numClassesInModel && wasteLabels.isNotEmpty() -> wasteLabels
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

    /**
     * Runs detection, distinguishing inference failure from "no objects".
     *
     * @param rotationDegrees clockwise rotation to apply before inference
     *   (from `imageProxy.imageInfo.rotationDegrees`). `0` = bitmap already upright.
     */
    suspend fun detectResult(bitmap: Bitmap, rotationDegrees: Int = 0): DetectionOutcome =
        withContext(Dispatchers.Default) {
            if (closed || !isInitialized) {
                return@withContext DetectionOutcome.Failure("Detector not initialized or closed")
            }
            if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
                return@withContext DetectionOutcome.Failure("Invalid bitmap (recycled or empty)")
            }

            stateMutex.withLock {
                if (closed) return@withContext DetectionOutcome.Failure("Detector closed")
                val interp = interpreter ?: return@withContext DetectionOutcome.Failure("No interpreter")
                val inBuf = inputBuffer ?: return@withContext DetectionOutcome.Failure("No input buffer")
                val outBuf = outputBuffer ?: return@withContext DetectionOutcome.Failure("No output buffer")
                val lbBitmap = letterboxBitmap ?: return@withContext DetectionOutcome.Failure("No canvas")

                var rotated: Bitmap? = null
                try {
                    val upright = if (rotationDegrees % 360 != 0) {
                        rotated = rotateBitmap(bitmap, rotationDegrees)
                        rotated
                    } else {
                        bitmap
                    }
                    val lb = letterbox(upright, inputSize)
                    inBuf.rewind()
                    val inputTensor = interp.getInputTensor(0)
                    if (inputTensor.dataType() == DataType.FLOAT32) {
                        bitmapToFloatByteBuffer(lbBitmap, inBuf)
                    } else {
                        // Quantized input: honor the model's scale/zero-point (§3.34).
                        // q = (pixel/255) / scale + zeroPoint, clamped to [0,255].
                        val qp = inputTensor.quantizationParams()
                        bitmapToQuantizedByteBuffer(lbBitmap, inBuf, qp.scale, qp.zeroPoint)
                    }

                    outBuf.rewind()
                    interp.run(inBuf, outBuf)

                    outBuf.rewind()
                    val outputShape = interp.getOutputTensor(0).shape()
                    val rawOutput = readFloatOutput(interp, outBuf, outputShape)

                    val detections = parseDetections(rawOutput, outputShape, lb.padX, lb.padY, lb.scaledW, lb.scaledH)
                    DetectionOutcome.Success(detections)
                } catch (e: Exception) {
                    Log.e(TAG, "YOLO detection failed", e)
                    DetectionOutcome.Failure("Inference failed: ${e.message}", e)
                } finally {
                    rotated?.recycle()
                    if (closed) {
                        cleanupInternal()
                    }
                }
            }
        }

    /**
     * Compat path: returns `emptyList()` on any failure. Prefer [detectResult],
     * which preserves the error. Kept because the `YoloScreen` call site consumes a
     * plain list; new callers must use [detectResult].
     */
    suspend fun detect(bitmap: Bitmap, rotationDegrees: Int = 0): List<Detection> =
        when (val outcome = detectResult(bitmap, rotationDegrees)) {
            is DetectionOutcome.Success -> outcome.detections
            is DetectionOutcome.Failure -> {
                Log.w(TAG, "detect() swallowing failure for compat: ${outcome.message}")
                emptyList()
            }
        }

    /**
     * Reads the output tensor as floats regardless of dtype (§3.34). UINT8 outputs
     * are dequantized via the tensor's own scale/zero-point
     * (`f = scale * (q - zeroPoint)`); the old code reinterpreted the bytes as
     * floats, producing garbage confidences on quantized models.
     */
    private fun readFloatOutput(
        interp: Interpreter,
        outBuf: ByteBuffer,
        outputShape: IntArray
    ): FloatArray {
        val size = outputShape.fold(1) { acc, i -> acc * i }
        if (interp.getOutputTensor(0).dataType() == DataType.FLOAT32) {
            val cached = rawOutputArray
            val dest = if (cached != null && cached.size == size) cached else FloatArray(size)
            outBuf.asFloatBuffer().get(dest)
            return dest
        }
        val qp = interp.getOutputTensor(0).quantizationParams()
        val out = FloatArray(size)
        for (i in 0 until size) {
            val q = outBuf.get().toInt() and 0xFF
            out[i] = qp.scale * (q - qp.zeroPoint)
        }
        return out
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

    /**
     * Scale/zero-point-aware quantized input writer (§3.34). Falls back to raw
     * byte copy only when the scale is degenerate (0) to avoid divide-by-zero.
     */
    private fun bitmapToQuantizedByteBuffer(
        bitmap: Bitmap,
        buffer: ByteBuffer,
        scale: Float,
        zeroPoint: Int
    ) {
        if (scale == 0f) {
            Log.w(TAG, "Quantized input scale is 0; falling back to raw bytes")
            bitmapToByteByteBuffer(bitmap, buffer)
            return
        }
        val pixels = pixelsBuffer ?: IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.put(((r / scale + zeroPoint).toInt().coerceIn(0, 255)).toByte())
            buffer.put(((g / scale + zeroPoint).toInt().coerceIn(0, 255)).toByte())
            buffer.put(((b / scale + zeroPoint).toInt().coerceIn(0, 255)).toByte())
        }
    }

    /** Rotates [src] clockwise by [degrees]; caller must recycle the result. */
    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate((degrees % 360).toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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
                var rawScore = if (isChannelFirst) {
                    raw[(4 + c) * numDetections + d]
                } else {
                    raw[d * valuesPerDetection + 4 + c]
                }
                // Pre-argmax logit handling (§3.34): normalize EACH class score
                // before comparing. The old code picked the argmax over raw logits
                // and sigmoided only the winner, which misranks when classes have
                // mixed logit/probability scales.
                if (rawScore > 1.0f || rawScore < 0.0f) {
                    rawScore = 1.0f / (1.0f + kotlin.math.exp(-rawScore))
                }
                if (rawScore > maxScore) {
                    maxScore = rawScore
                    maxClassIdx = c
                }
            }

            val score = maxScore

            if (score < confidenceThreshold) continue

            // Coordinate scale is EXPLICIT via [coordinatesNormalized] (model metadata),
            // not the old `cx <= 1` value heuristic which mis-scaled small near-origin
            // pixel boxes by ×640 (§3.34).
            val (absCx, absCy, absW, absH) = if (coordinatesNormalized) {
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
            .filter { det ->
                // COCO-fallback guard (KEEP — load-bearing safety, see §3.47): in
                // 80-label mode suppress non-waste objects (person, car, …) instead of
                // surfacing them as waste items. In particular this stops stock-COCO
                // class 0 (person) from being misreported as waste label 0 (battery).
                // Long-term fix belongs in scripts/export (never ship COCO as the waste
                // model); this guard stays regardless so a mis-packaged asset degrades
                // to fewer boxes, not wrong boxes.
                if (labels.size == 80) {
                    com.agrelius.wasegmul.WasteMapping.getCategory(det.label) !=
                        com.agrelius.wasegmul.WasteMapping.UNKNOWN
                } else true
            }
            .take(MAX_DISPLAYED_DETECTIONS)
    }

    /**
     * Per-class NMS (documented choice, §3.34): overlapping boxes of DIFFERENT
     * classes are intentionally kept (a bottle next to a can may legitimately
     * overlap in a cluttered frame); same-class duplicates are suppressed.
     * Cross-class NMS was evaluated and rejected — it deleted valid adjacent items.
     * Exact-duplicate candidates (same class + near-identical box) are deduped first.
     */
    private fun nonMaxSuppression(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val deduped = detections.distinctBy { det ->
            val b = det.boundingBox
            // Quantize to ~0.5px at 640 to collapse float-identical candidates.
            listOf(
                det.classIndex,
                (b.left * 1280).toInt(), (b.top * 1280).toInt(),
                (b.right * 1280).toInt(), (b.bottom * 1280).toInt()
            )
        }
        return deduped.groupBy { it.classIndex }
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
        // Single-mutex close (§2.8): tryLock, never block the Main thread.
        if (stateMutex.tryLock()) {
            try {
                cleanupInternal()
            } finally {
                stateMutex.unlock()
            }
        }
        // If stateMutex is currently held, the in-flight detect()/ensureInitialized()
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
