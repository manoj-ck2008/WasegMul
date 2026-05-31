package com.agrelius.wasegmul.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waste_history")
data class WasteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val subclass: String,
    val confidence: Float,
    val estimatedWeight: Double = 0.05,
    val featureVector: String? = null, // Base64 encoded or string representation
    val imagePath: String? = null, // Local file path if sharing is allowed
    val feedback: String? = null,
    val correctedSubclass: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
