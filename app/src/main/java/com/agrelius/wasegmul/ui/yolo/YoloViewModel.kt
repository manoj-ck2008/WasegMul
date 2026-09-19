package com.agrelius.wasegmul.ui.yolo

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agrelius.wasegmul.WasteKnowledgeBase
import com.agrelius.wasegmul.WasteMapping
import com.agrelius.wasegmul.ml.Detection
import com.agrelius.wasegmul.ml.YoloDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Raw per-frame smoother input (normalized 0..1 box coords).
 *
 * Plain floats (no `RectF`) so the smoother stays JVM-testable.
 */
data class RawDetection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val classIndex: Int = 0
)

/**
 * A detection track held across frames (normalized 0..1 box coords).
 */
data class StableDetection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val lastSeenMs: Long,
    val classIndex: Int = 0
)

/**
 * Temporal smoother for live YOLO boxes: kills flicker and shake-jitter.
 *
 * - **Min-hold**: tracks survive [holdMs] after their last sighting, so a
 *   single empty frame never clears the overlay ("often nothing detected").
 * - **Hysteresis**: a new track needs [enterThreshold], but a matching live
 *   track is refreshed down to [keepThreshold] — boxes stop popping at the edge.
 * - **Deadband**: box shifts smaller than [deadbandFrac] (normalized units)
 *   are ignored, so hand shake doesn't make boxes dance; larger moves snap.
 *
 * Matching is same-label (case-insensitive) + IoU overlap. Allocation-light:
 * tracks are reused in place; each [update] allocates only the emitted list.
 */
class DetectionSmoother(
    @Volatile var enterThreshold: Float = 0.45f,
    @Volatile var keepThreshold: Float = 0.35f,
    val holdMs: Long = 600L,
    val deadbandFrac: Float = 0.02f,
    val maxTracks: Int = 10
) {
    private data class Track(
        var label: String,
        var confidence: Float,
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var lastSeenMs: Long,
        var classIndex: Int
    )

    private val tracks = mutableListOf<Track>()

    fun update(raw: List<RawDetection>, nowMs: Long): List<StableDetection> {
        // Highest confidence claims tracks first (deterministic matching).
        val ordered = raw.sortedByDescending { it.confidence }
        for (det in ordered) {
            val match = bestMatch(det)
            if (match != null) {
                // Hysteresis: refresh a known track below the enter bar.
                if (det.confidence >= keepThreshold) {
                    match.lastSeenMs = nowMs
                    match.confidence = det.confidence
                    match.classIndex = det.classIndex
                    // Deadband: freeze tiny shake-jitter, snap real moves.
                    val shift = maxOf(
                        kotlin.math.abs(det.left - match.left),
                        kotlin.math.abs(det.top - match.top),
                        kotlin.math.abs(det.right - match.right),
                        kotlin.math.abs(det.bottom - match.bottom)
                    )
                    if (shift >= deadbandFrac) {
                        match.left = det.left
                        match.top = det.top
                        match.right = det.right
                        match.bottom = det.bottom
                    }
                }
            } else if (det.confidence >= enterThreshold) {
                tracks.add(
                    Track(
                        label = det.label,
                        confidence = det.confidence,
                        left = det.left,
                        top = det.top,
                        right = det.right,
                        bottom = det.bottom,
                        lastSeenMs = nowMs,
                        classIndex = det.classIndex
                    )
                )
            }
        }
        // Evict stale tracks (min-hold expiry).
        tracks.removeAll { nowMs - it.lastSeenMs > holdMs }
        return tracks.sortedByDescending { it.confidence }
            .take(maxTracks)
            .map {
                StableDetection(
                    label = it.label,
                    confidence = it.confidence,
                    left = it.left,
                    top = it.top,
                    right = it.right,
                    bottom = it.bottom,
                    lastSeenMs = it.lastSeenMs,
                    classIndex = it.classIndex
                )
            }
    }

    private fun bestMatch(det: RawDetection): Track? {
        var best: Track? = null
        var bestIou = IOU_MATCH_MIN
        for (track in tracks) {
            if (!track.label.trim().equals(det.label.trim(), ignoreCase = true)) continue
            val iou = iou(
                track.left, track.top, track.right, track.bottom,
                det.left, det.top, det.right, det.bottom
            )
            if (iou > bestIou) {
                bestIou = iou
                best = track
            }
        }
        return best
    }

    companion object {
        private const val IOU_MATCH_MIN = 0.25f

        internal fun iou(
            l1: Float, t1: Float, r1: Float, b1: Float,
            l2: Float, t2: Float, r2: Float, b2: Float
        ): Float {
            val interL = maxOf(l1, l2)
            val interT = maxOf(t1, t2)
            val interR = minOf(r1, r2)
            val interB = minOf(b1, b2)
            val inter = (interR - interL).coerceAtLeast(0f) * (interB - interT).coerceAtLeast(0f)
            if (inter <= 0f) return 0f
            val a1 = (r1 - l1).coerceAtLeast(0f) * (b1 - t1).coerceAtLeast(0f)
            val a2 = (r2 - l2).coerceAtLeast(0f) * (b2 - t2).coerceAtLeast(0f)
            val union = a1 + a2 - inter
            return if (union <= 0f) 0f else inter / union
        }
    }
}

/**
 * Per-detection guidance card payload: Category + disposal action +
 * one-line handling tip. Reuses [WasteMapping] and [WasteKnowledgeBase];
 * no new science. Disposal action is a closed vocabulary.
 */
data class YoloGuidance(
    val category: String,
    val disposalAction: String,
    val tip: String
)

fun guidanceForDetection(label: String): YoloGuidance {
    val category = runCatching { WasteMapping.getCategory(label) }
        .getOrDefault(WasteMapping.UNKNOWN)
    val disposalAction = when {
        category.equals("Recyclable", ignoreCase = true) -> "Recycle"
        category.equals("Organic", ignoreCase = true) -> "Compost"
        category.equals(WasteMapping.TRASH, ignoreCase = true) ||
            category.equals(WasteMapping.RESIDUAL, ignoreCase = true) -> "Discard"
        category.equals("E-Waste", ignoreCase = true) ||
            category.equals("Hazardous", ignoreCase = true) -> "Drop-off"
        else -> "Check"
    }
    val guide = runCatching { WasteKnowledgeBase.getInfo(category, label).disposalGuide }
        .getOrDefault("")
    // First non-blank bullet, de-bulleted to a single line.
    val tip = guide.lineSequence()
        .map { it.trim().trimStart('•', '-', '*', ' ').trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: "Check local disposal guidelines before binning."
    return YoloGuidance(category = category, disposalAction = disposalAction, tip = tip)
}

/**
 * YOLO live-detection state holder (separate file: previously nested in the
 * screen, which hid the Main-thread contract).
 *
 * Threading: [detectFrame] must be called off the Main thread
 * (Dispatchers.Default). [YoloDetector.detect] is itself Main-safe, but the
 * frame snapshot bookkeeping around it is not free, so callers dispatch.
 */
class YoloViewModel : ViewModel() {
    @Volatile
    private var detector: YoloDetector? = null
    private val initGuard = Mutex()
    private val detectMutex = Mutex()
    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Volatile
    private var frameCount = 0
    @Volatile
    private var lastFpsTime = System.currentTimeMillis()

    /** Temporal smoother: holds boxes across gaps, damps shake-jitter. */
    private val smoother = DetectionSmoother()
    /** Reused per-frame input buffer (no per-frame list allocation). */
    private val rawReuse = ArrayList<RawDetection>(16)

    suspend fun initDetector(context: android.content.Context) {
        initGuard.withLock {
            if (detector != null) return
            try {
                val d = YoloDetector(context.applicationContext)
                d.ensureInitialized()
                detector = d
                _error.value = null
            } catch (t: Throwable) {
                detector = null
                _error.value = "YOLO model not available. Check bundled models and storage."
                Log.w("YoloVM", "YOLO init failed: ${t.message}", t)
            }
        }
    }

    fun applyConfidenceThreshold(value: Float) {
        detector?.setConfidenceThreshold(value)
        // Smoother follows the user tuning: enter at the detector bar,
        // keep 0.10 below it (hysteresis against edge flicker).
        smoother.enterThreshold = value
        smoother.keepThreshold = (value - 0.10f).coerceAtLeast(0.10f)
    }

    suspend fun detectFrame(bitmap: Bitmap) {
        // Drop frames when busy instead of queueing (prevents multi-second lag/OOM).
        if (!detectMutex.tryLock()) return
        try {
            val d = detector ?: return
            try {
                val results = d.detect(bitmap)
                // Temporal smoothing: hold boxes briefly across empty frames,
                // freeze sub-deadband shake-jitter. Reuses [rawReuse].
                rawReuse.clear()
                for (r in results) {
                    val b = r.boundingBox
                    rawReuse.add(
                        RawDetection(
                            label = r.label,
                            confidence = r.confidence,
                            left = b.left,
                            top = b.top,
                            right = b.right,
                            bottom = b.bottom,
                            classIndex = r.classIndex
                        )
                    )
                }
                val now = System.currentTimeMillis()
                _detections.value = smoother.update(rawReuse, now).map { s ->
                    Detection(
                        label = s.label,
                        confidence = s.confidence,
                        boundingBox = RectF(s.left, s.top, s.right, s.bottom),
                        classIndex = s.classIndex
                    )
                }

                frameCount++
                val elapsed = now - lastFpsTime
                if (elapsed >= 1000) {
                    _fps.value = frameCount * 1000f / elapsed
                    frameCount = 0
                    lastFpsTime = now
                }
            } catch (t: Throwable) {
                Log.e("YoloVM", "Detection failed", t)
            }
        } finally {
            detectMutex.unlock()
        }
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
        detector?.close()
        detector = null
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
        detector = null
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return YoloViewModel() as T
        }
    }
}
