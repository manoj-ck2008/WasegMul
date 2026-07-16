package com.agrelius.wasegmul.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-persisted classification record.
 *
 * [topPredictions] stores the encoded top-K subclass predictions so the ResultScreen can
 * render the same information when a record is reopened from history (see PredictionCodec).
 */
@Entity(tableName = "waste_history")
data class WasteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val subclass: String,
    val confidence: Float,
    val estimatedWeight: Double = 0.05,
    val featureVector: String? = null,
    val imagePath: String? = null,
    val feedback: String? = null,
    val correctedSubclass: String? = null,
    val topPredictions: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
