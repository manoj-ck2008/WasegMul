package com.agrelius.wasegmul.data

import com.agrelius.wasegmul.WasteRecord as CommonWasteRecord

fun WasteRecord.toCommon(): CommonWasteRecord = CommonWasteRecord(
    id = id,
    category = category,
    subclass = subclass,
    confidence = confidence,
    estimatedWeight = estimatedWeight,
    featureVector = featureVector,
    imagePath = imagePath,
    feedback = feedback,
    correctedSubclass = correctedSubclass,
    timestamp = timestamp
)

fun CommonWasteRecord.toEntity(): WasteRecord = WasteRecord(
    id = id,
    category = category,
    subclass = subclass,
    confidence = confidence,
    estimatedWeight = estimatedWeight,
    featureVector = featureVector,
    imagePath = imagePath,
    feedback = feedback,
    correctedSubclass = correctedSubclass,
    timestamp = timestamp
)
