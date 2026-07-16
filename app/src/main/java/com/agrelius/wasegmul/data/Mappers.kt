package com.agrelius.wasegmul.data

import com.agrelius.wasegmul.WasteRecord as SharedWasteRecord

/**
 * Mappers between the Room entity [WasteRecord] and the shared domain model.
 * Fields are 1:1; keep these in sync when either model changes.
 */

fun WasteRecord.toCommon(): SharedWasteRecord = SharedWasteRecord(
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
    timestamp = timestamp
)

fun SharedWasteRecord.toEntity(): WasteRecord = WasteRecord(
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
    timestamp = timestamp
)
