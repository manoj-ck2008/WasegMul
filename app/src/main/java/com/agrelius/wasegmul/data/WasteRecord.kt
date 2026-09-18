package com.agrelius.wasegmul.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.agrelius.wasegmul.utils.SafeLog

/**
 * Room-persisted classification record.
 *
 * [topPredictions] stores the encoded top-K subclass predictions so the ResultScreen can
 * render the same information when a record is reopened from history (see PredictionCodec).
 *
 * ### Write-time validation (§3.37)
 * The shared domain model (`CommonModels.WasteRecord`) enforces `require()` guards
 * (confidence finite in `0..1`, weight finite `>= 0`). This entity deliberately does
 * **not** `require()` in its constructor: throwing at Room read time would crash every
 * `Flow` collector (`Mappers.toCommon`, `WasteRepository.allHistory`). Instead:
 * - writers MUST pass records through [sanitizedForWrite] (coerce + clamp + log), and
 * - readers MUST use the guarded `toCommon()` mapper (never throws).
 * Legacy rows written before validation are healed on read (with a warning log), never
 * by per-read DB rewrites.
 *
 * ### CHECK-constraint guidance
 * SQLite `CHECK(confidence BETWEEN 0 AND 1)` / `CHECK(estimatedWeight >= 0)` constraints
 * are intentionally NOT added to this table: adding them requires a table rebuild
 * migration (`v11 -> v12` copy-drop-rename) that risks existing user history for no
 * additional safety beyond [sanitizedForWrite]. If constraints are ever added, they must
 * ship with that rebuild migration plus a Room schema test.
 *
 * ### Vocabulary (CONTEXT.md)
 * New code uses `Residual` for the non-recyclable general-waste category. `Trash` is
 * retained as accepted **compat input** only, because the `category_classes.txt` label
 * asset and existing `waste_history` rows emit `Trash` (renaming the asset without a
 * coordinated model/label/shared-taxonomy change would silently remap predictions).
 * See [CATEGORY_RESIDUAL] / [CATEGORY_TRASH_COMPAT].
 */
@Entity(
    tableName = "waste_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["subclass"]),
        Index(value = ["feedback"])
    ]
)
data class WasteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val subclass: String,
    val confidence: Float,
    val estimatedWeight: Double = DEFAULT_WEIGHT_KG,
    val featureVector: String? = null,
    val imagePath: String? = null,
    val feedback: String? = null,
    val correctedSubclass: String? = null,
    val topPredictions: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    // v11: first-class barcode provenance (replaces featureVector pipe-smuggling).
    val source: String = SOURCE_CAMERA,
    val productName: String? = null,
    val barcode: String? = null
) {
    companion object {
        private const val TAG = "WasteRecord"

        /**
         * Single source of truth for the default weight estimate (0.05 kg = 50 g).
         * Mirrors the `0.05` fallback in shared `WasteMapping`/`CommonModels`; if either
         * changes, update all three together (they are intentionally one value).
         */
        const val DEFAULT_WEIGHT_KG = 0.05

        /** CONTEXT.md category for non-recyclable general waste. Use for new code. */
        const val CATEGORY_RESIDUAL = "Residual"

        /**
         * Legacy label emitted by `category_classes.txt` + historical rows.
         * Accepted on read/write for compat; prefer [CATEGORY_RESIDUAL] for new code.
         */
        const val CATEGORY_TRASH_COMPAT = "Trash"

        /** Record origin vocabulary (closed set — do not invent new values). */
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_BARCODE = "barcode"
        const val SOURCE_VISUAL_ML = "visual_ml"
        const val SOURCE_USER_IDENTIFIED = "user_identified"

        /** User-feedback vocabulary (closed set — free strings fork analytics). */
        const val FEEDBACK_CORRECT = "correct"
        const val FEEDBACK_INCORRECT = "incorrect"

        /** Accepted [source] values. */
        val KNOWN_SOURCES = setOf(SOURCE_CAMERA, SOURCE_BARCODE, SOURCE_VISUAL_ML, SOURCE_USER_IDENTIFIED)

        /** Accepted [feedback] values. */
        val KNOWN_FEEDBACK = setOf(FEEDBACK_CORRECT, FEEDBACK_INCORRECT)

        /**
         * Returns `true` when [confidence] is finite and inside `0..1`.
         * `NaN` fails this check explicitly (`NaN in 0f..1f` is false, but every
         * `< NaN` comparison is also false, so call this instead of comparing).
         */
        fun isValidConfidence(confidence: Float): Boolean =
            confidence.isFinite() && confidence in 0f..1f

        /** Returns `true` when [weightKg] is finite and `>= 0`. */
        fun isValidWeight(weightKg: Double): Boolean =
            weightKg.isFinite() && weightKg >= 0.0
    }
}

/**
 * Coerce-and-log write guard (§3.37): returns a copy safe to insert.
 *
 * - Non-finite/out-of-range [WasteRecord.confidence] → clamped to `0..1` (NaN → `0f`).
 * - Non-finite/negative [WasteRecord.estimatedWeight] → [WasteRecord.DEFAULT_WEIGHT_KG].
 * - Unknown [WasteRecord.source]/[WasteRecord.feedback] → kept (never drop user data),
 *   but logged so typo forks are visible in logcat.
 * - Non-positive [WasteRecord.timestamp] (legacy `0L` = 1970) → `nowMs`.
 *
 * Every correction is logged with [TAG] so silent data repair stays observable.
 */
fun WasteRecord.sanitizedForWrite(
    nowMs: Long = System.currentTimeMillis()
): WasteRecord {
    var out = this
    if (!WasteRecord.isValidConfidence(confidence)) {
        val coerced = when {
            !confidence.isFinite() -> 0f
            confidence < 0f -> 0f
            else -> 1f
        }
        SafeLog.w("WasteRecord", "Coercing out-of-range confidence=$confidence to $coerced (id=$id)")
        out = out.copy(confidence = coerced)
    }
    if (!WasteRecord.isValidWeight(estimatedWeight)) {
        SafeLog.w(
            "WasteRecord",
            "Coercing invalid weight=$estimatedWeight to default=${WasteRecord.DEFAULT_WEIGHT_KG} (id=$id)"
        )
        out = out.copy(estimatedWeight = WasteRecord.DEFAULT_WEIGHT_KG)
    }
    if (source !in WasteRecord.KNOWN_SOURCES) {
        SafeLog.w("WasteRecord", "Unknown source='$source' kept as-is (id=$id); expected one of ${WasteRecord.KNOWN_SOURCES}")
    }
    if (feedback != null && feedback !in WasteRecord.KNOWN_FEEDBACK) {
        SafeLog.w("WasteRecord", "Unknown feedback='$feedback' kept as-is (id=$id); expected one of ${WasteRecord.KNOWN_FEEDBACK}")
    }
    if (timestamp <= 0L) {
        SafeLog.w("WasteRecord", "Healing non-positive timestamp=$timestamp to now (id=$id)")
        out = out.copy(timestamp = nowMs)
    }
    return out
}
