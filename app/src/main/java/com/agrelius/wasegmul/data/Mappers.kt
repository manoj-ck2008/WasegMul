package com.agrelius.wasegmul.data

import com.agrelius.wasegmul.WasteRecord as SharedWasteRecord
import com.agrelius.wasegmul.utils.SafeLog

private const val MAPPERS_TAG = "Mappers"

/**
 * Mappers between the Room entity [WasteRecord] and the shared domain model.
 * Fields are 1:1; keep these in sync when either model changes.
 *
 * ### Guarded reads (§3.37)
 * The shared model enforces `require()` guards (confidence `0..1`, weight `>= 0`),
 * so a naive field copy would throw at read time and crash every `Flow` collector
 * (`WasteRepository.allHistory`). [toCommon] therefore coerces instead of throwing:
 * non-finite/out-of-range confidence → clamped (`NaN` → `0f`), invalid weight →
 * [WasteRecord.DEFAULT_WEIGHT_KG]. Each repair is logged so corrupt rows stay
 * observable. Rows are never rewritten in the DB on read (no per-read rewrite that
 * could mask the underlying write bug — fix writers via [sanitizedForWrite]).
 */

/** Guarded entity → shared mapping. Never throws for out-of-range numerics. */
fun WasteRecord.toCommon(): SharedWasteRecord {
    var confidence = confidence
    if (!WasteRecord.isValidConfidence(confidence)) {
        val coerced = when {
            !confidence.isFinite() -> 0f
            confidence < 0f -> 0f
            else -> 1f
        }
        SafeLog.w(MAPPERS_TAG, "toCommon: coercing confidence=$confidence to $coerced (id=$id)")
        confidence = coerced
    }
    var weight = estimatedWeight
    if (!WasteRecord.isValidWeight(weight)) {
        SafeLog.w(MAPPERS_TAG, "toCommon: coercing weight=$weight to default (id=$id)")
        weight = WasteRecord.DEFAULT_WEIGHT_KG
    }
    return SharedWasteRecord(
        id = id,
        category = category,
        subclass = subclass,
        confidence = confidence,
        estimatedWeight = weight,
        featureVector = featureVector,
        imagePath = imagePath,
        feedback = feedback,
        correctedSubclass = correctedSubclass,
        topPredictions = topPredictions,
        timestamp = timestamp,
        source = source,
        productName = productName,
        barcode = barcode
    )
}

/**
 * Shared → entity mapping (write path). Heals legacy `timestamp = 0L` (1970 sentinel
 * from the shared model's `0L = unset` default) to `nowMs` with a warning log.
 *
 * Timestamp-heal policy (LOW audit note): the heal deliberately lives on the WRITE
 * path, not in a `v11 -> v12` backfill migration — a migration cannot recover the
 * true scan time (any backfilled value would fabricate history), whereas write-path
 * healing plus the log preserves honesty. Numeric validation is NOT duplicated here;
 * writers must call [sanitizedForWrite] before insert.
 */
fun SharedWasteRecord.toEntity(nowMs: Long = System.currentTimeMillis()): WasteRecord {
    val healedTimestamp = if (timestamp > 0L) timestamp else {
        SafeLog.w(MAPPERS_TAG, "toEntity: healing non-positive timestamp=$timestamp to now")
        nowMs
    }
    return WasteRecord(
    id = id,
    category = category,
    subclass = subclass,
    confidence = confidence,
    estimatedWeight = estimatedWeight,
    featureVector = featureVector,
    imagePath = imagePath,
    feedback = feedback,
    correctedSubclass = correctedSubclass,
    topPredictions = topPredictions,
    timestamp = healedTimestamp,
    source = source,
    productName = productName,
    barcode = barcode
    )
}
