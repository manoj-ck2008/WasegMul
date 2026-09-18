package com.agrelius.wasegmul

import kotlinx.serialization.Serializable

/**
 * Cross-platform domain models shared between the Android and iOS targets.
 *
 * These mirror the Room-persisted entity (see `data/WasteRecord.kt` in the Android module)
 * and the in-memory prediction payloads produced by the ML pipeline.
 *
 * ## Confidence policy (single rule, enforced at every seam)
 * Every confidence is a finite [Float] in `0..1` (calibrated probability, NOT logits).
 * - [WasteRecord], [ClassificationResult], [InternalResult]: strict `require` in `init`
 *   (fail fast; producers that can emit NaN — e.g. a corrupt model tensor — degrade via
 *   the caller's try/catch, never persist a NaN).
 * - [PredictionResult]: raw pre-arbitration boundary fed directly by classifier output,
 *   so `init` deliberately does NOT range-check (a `require` here would crash the
 *   pipeline on uncalibrated tensors instead of degrading to UNCERTAIN).
 *   [MLArbitrator] sanitises via [PredictionResult.sanitized] on entry (NaN → `0f`,
 *   out-of-range clamped, top lists filtered/capped). Use [requireUnitConfidence] to
 *   validate anywhere else.
 *
 * ## Vocabulary
 * `source` / `feedback` are persisted as raw strings (Room columns, analytics history and
 * OFF wire format), so they stay `String` rather than enums — but new code MUST use the
 * [WasteSource] / [WasteFeedback] constants and [WasteSource.isKnown]/[WasteFeedback.isKnown]
 * instead of inventing literals (typo'd literals silently fork analytics). Enums were
 * considered and rejected for these two fields for storage-compat reasons; [ClassificationMode]
 * (in-memory only) IS an enum, as are the [DisposalAction] constants' validation helpers
 * in [PackagingWasteMapper].
 *
 * ## `subcategory` vs `subclass`
 * CONTEXT.md canonical term is **Subclass**. The stored property is still named
 * `subcategory` (Room column + OFF wire compat; renaming would break persisted history
 * and every named-argument call site). The [PredictionResult.subclass],
 * [PredictionResult.subclassConfidence] and [PredictionResult.topSubclasses] aliases are
 * the canonical accessors — new code MUST use them. A storage migration to `subclass`
 * is tracked but out of scope here.
 *
 * ## Serialization
 * [WasteRecord], [BarcodeEvidence], [WasteInfo] and [EcoImpactMetrics][EcoImpactMetrics]
 * are `@Serializable` (primitives/strings only — safe for cache export and the iOS bridge).
 * [PredictionResult], [ClassificationResult] and [InternalResult] carry
 * `List<Pair<String, Float>>` top lists (`Pair` is Obj-C-hostile and not `@Serializable`),
 * so they are deliberately NOT serializable; persist top lists ONLY via [PredictionCodec]
 * (see [WasteRecord.decodedTopPredictions]/[WasteRecord.encodeTopPredictions]).
 */

/** Validates a calibrated confidence: finite and in `0..1`. Throws [IllegalArgumentException] otherwise. */
fun requireUnitConfidence(name: String, value: Float): Float {
    require(value.isFinite() && value in 0f..1f) { "$name must be finite and in 0..1, was $value" }
    return value
}

/** Validates a top-K list: finite `0..1` confidences, non-blank labels, capped size. */
fun requireTopList(name: String, tops: List<Pair<String, Float>>, maxSize: Int = 25): List<Pair<String, Float>> {
    require(tops.size <= maxSize) { "$name exceeds cap of $maxSize entries (${tops.size})" }
    tops.forEach { (label, conf) ->
        require(label.isNotBlank()) { "$name contains a blank label" }
        requireUnitConfidence("$name['$label']", conf)
    }
    return tops
}

/** Closed vocabulary for [WasteRecord.source]. */
object WasteSource {
    const val CAMERA = "camera"
    const val BARCODE = "barcode"
    const val VISUAL_ML = "visual_ml"
    const val USER_IDENTIFIED = "user_identified"

    /** All known origins, including legacy pipeline values that persist in history. */
    val ALL: Set<String> = setOf(CAMERA, BARCODE, VISUAL_ML, USER_IDENTIFIED)

    /** Case-insensitive membership test for analytics grouping; unknown → caller buckets as "other". */
    fun isKnown(source: String?): Boolean =
        source != null && ALL.any { it.equals(source.trim(), ignoreCase = true) }
}

/** Closed vocabulary for [WasteRecord.feedback]. */
object WasteFeedback {
    const val CORRECT = "correct"
    const val INCORRECT = "incorrect"

    val ALL: Set<String> = setOf(CORRECT, INCORRECT)

    fun isKnown(feedback: String?): Boolean =
        feedback != null && ALL.any { it.equals(feedback.trim(), ignoreCase = true) }

    /** True only for an explicit user "correct" mark (case-insensitive, trimmed). */
    fun isCorrectMark(feedback: String?): Boolean =
        feedback?.trim().equals(CORRECT, ignoreCase = true)
}

/** A persisted classification record. */
@Serializable
data class WasteRecord(
    val id: Long = 0,
    /** Canonical category; see [WasteMapping.KNOWN_CATEGORIES]. Range: closed vocab. */
    val category: String,
    /** Fine-grained material identity (canonical domain term: Subclass). */
    val subclass: String,
    /** Calibrated confidence in `0..1`. Units: probability. */
    val confidence: Float,
    /**
     * Estimated unit weight in **kg** (NOT grams). Default = [WasteMapping.DEFAULT_WEIGHT_KG]
     * (single source of truth — do not re-hard-code). Range: finite, `>= 0`.
     */
    val estimatedWeight: Double = WasteMapping.DEFAULT_WEIGHT_KG,
    /**
     * Legacy pipe-tag storage (`"barcode:code|name|brand"`, `"feature:..."`).
     * Do NOT parse inline — consumers must go through a single parser; new code should
     * prefer the dedicated [source]/[productName]/[barcode] fields and [topPredictions].
     */
    val featureVector: String? = null,
    val imagePath: String? = null,
    /** User verdict; closed vocab [WasteFeedback] (`"correct"`/`"incorrect"`), null = unrated. */
    val feedback: String? = null,
    /** User-corrected subclass label; null/blank = no correction. Must be a subclass, never a category. */
    val correctedSubclass: String? = null,
    /**
     * Serialised "label|confidence;label|confidence" top-subclass list.
     * Encode/decode ONLY via [encodeTopPredictions]/[decodedTopPredictions] (single path
     * through [PredictionCodec]); never hand-roll the pipe format.
     */
    val topPredictions: String? = null,
    /**
     * Epoch millis of classification. `0L` = legacy unset (healed to now on read by the
     * repository); negative values are rejected.
     */
    val timestamp: Long = 0L,
    /** Record origin; closed vocab [WasteSource]. */
    val source: String = WasteSource.CAMERA,
    /** Barcode-resolved product name; null = not a barcode record. Never synthesise one. */
    val productName: String? = null,
    /** Raw GS1 barcode digits; null = not a barcode record. */
    val barcode: String? = null
) {
    init {
        requireUnitConfidence("confidence", confidence)
        require(estimatedWeight.isFinite() && estimatedWeight >= 0.0) { "weight must be finite >= 0" }
        require(timestamp >= 0L) { "timestamp must be >= 0 (0 = legacy unset)" }
    }

    /** Decodes [topPredictions] via the single sanctioned [PredictionCodec] path. */
    fun decodedTopPredictions(): List<Pair<String, Float>> = PredictionCodec.decode(topPredictions)

    companion object {
        /** Encodes a top-K list via the single sanctioned [PredictionCodec] path. */
        fun encodeTopPredictions(predictions: List<Pair<String, Float>>): String =
            PredictionCodec.encode(predictions)
    }
}

/** Raw merged output of the category + subclass classifiers before arbitration. */
data class PredictionResult(
    /** Raw category label; may be blank/`Unknown` in degraded modes. Validated by [MLArbitrator]. */
    val category: String,
    /**
     * Raw category confidence. Pre-arbitration boundary: may be uncalibrated or even NaN
     * straight from the tensor — the `0..1` range is enforced at the arbitration seam
     * ([sanitized]), never here, so corrupt model output degrades to UNCERTAIN instead of
     * crashing the pipeline.
     */
    val categoryConfidence: Float,
    /**
     * Legacy wire name for the subclass label. New code MUST use the [subclass] alias
     * (canonical CONTEXT.md term); this property is retained for Room/wire compat.
     */
    val subcategory: String,
    /** Raw subclass confidence; same finite-now / range-at-seam contract as [categoryConfidence]. */
    val subcategoryConfidence: Float,
    val topSubcategories: List<Pair<String, Float>> = emptyList(),
    /** Top category predictions (label → confidence) for uncertainty estimation. */
    val topCategories: List<Pair<String, Float>> = emptyList(),
    /** Dynamic user-facing message explaining the classification outcome. */
    val classificationMessage: String = "",
    /**
     * Pre-zeroing confidences preserved for analytics whenever arbitration commits to an
     * UNCERTAIN/UNKNOWN verdict (null = no zeroing happened; values are the raw inputs).
     * Lets dashboards distinguish "confident" from "confidently uncertain".
     */
    val rawCategoryConfidence: Float? = null,
    val rawSubcategoryConfidence: Float? = null
) {
    // Canonical domain aliases matching CONTEXT.md
    /** Canonical accessor for [subcategory]. */
    val subclass: String get() = subcategory
    /** Canonical accessor for [subcategoryConfidence]. */
    val subclassConfidence: Float get() = subcategoryConfidence
    /** Canonical accessor for [topSubcategories]. */
    val topSubclasses: List<Pair<String, Float>> get() = topSubcategories

    init {
        // Deliberately NO finite/range require on the raw confidences (see KDoc above):
        // this is the crash-free ingestion boundary; the arbitration seam enforces policy.
        rawCategoryConfidence?.let { requireUnitConfidence("rawCategoryConfidence", it) }
        rawSubcategoryConfidence?.let { requireUnitConfidence("rawSubcategoryConfidence", it) }
    }

    /**
     * Returns a copy safe for arbitration: NaN → `0f`, out-of-range clamped to `0..1`,
     * blank labels / non-finite entries dropped from top lists, lists capped at 25.
     * [rawCategoryConfidence]/[rawSubcategoryConfidence] are preserved untouched.
     */
    fun sanitized(): PredictionResult {
        fun clean(conf: Float): Float = when {
            conf.isNaN() -> 0f
            !conf.isFinite() -> 0f
            else -> conf.coerceIn(0f, 1f)
        }
        fun cleanTops(tops: List<Pair<String, Float>>): List<Pair<String, Float>> =
            tops.asSequence()
                .filter { (label, conf) -> label.isNotBlank() && conf.isFinite() }
                .map { (label, conf) -> label.trim() to conf.coerceIn(0f, 1f) }
                .take(25)
                .toList()
        return copy(
            categoryConfidence = clean(categoryConfidence),
            subcategoryConfidence = clean(subcategoryConfidence),
            topSubcategories = cleanTops(topSubcategories),
            topCategories = cleanTops(topCategories)
        )
    }
}

/**
 * Product packaging evidence obtained from barcode lookup.
 *
 * Fail-closed: [isComplete] defaults to `false` — a partially-resolved product must never
 * be mistaken for 0.98 ground truth because a caller forgot the flag.
 */
@Serializable
data class BarcodeEvidence(
    /** Product name from the barcode database. Blank names must be rejected upstream. */
    val productName: String,
    /** Resolved category (validated against [WasteMapping.isKnownCategory] at arbitration). */
    val category: String,
    /** Resolved subclass label. */
    val subclass: String,
    /** True only when packaging metadata is fully resolved. Default `false` (fail-closed). */
    val isComplete: Boolean = false
)

/** UI-facing classification payload (post-arbitration + knowledge-base lookup). */
data class ClassificationResult(
    val category: String,
    val subclass: String,
    /** Calibrated confidence in `0..1`. Units: probability. */
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>>,
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String,
    /** Dynamic message explaining the classification outcome to the user. */
    val classificationMessage: String = ""
) {
    init {
        requireUnitConfidence("confidence", confidence)
        requireTopList("topPredictions", topPredictions)
    }
}

/** Raw classifier output bridging the Android TFLite layer to shared types. */
data class InternalResult(
    val label: String,
    /** Calibrated confidence in `0..1`. Units: probability. */
    val confidence: Float,
    val topPredictions: List<Pair<String, Float>> = emptyList()
) {
    init {
        requireUnitConfidence("confidence", confidence)
        requireTopList("topPredictions", topPredictions)
    }
}

/** Knowledge-base payload returned for a (category, subclass) pair. */
@Serializable
data class WasteInfo(
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String
)
