package com.agrelius.wasegmul

import com.agrelius.wasegmul.data.WasteRecord as EntityRecord
import com.agrelius.wasegmul.data.toCommon
import com.agrelius.wasegmul.data.toEntity
import org.junit.Assert.*
import org.junit.Test

class MappersTest {

    @Test
    fun testEntityToCommon_andBack() {
        val now = 1700000000000L
        val entity = EntityRecord(
            id = 42L,
            category = "E-Waste",
            subclass = "Battery",
            confidence = 0.94f,
            estimatedWeight = 0.025,
            featureVector = "vec_123",
            imagePath = "/storage/image1.jpg",
            feedback = "correct",
            correctedSubclass = null,
            topPredictions = "Battery|0.94;PCB|0.03",
            timestamp = now
        )

        val common = entity.toCommon()

        assertEquals(42L, common.id)
        assertEquals("E-Waste", common.category)
        assertEquals("Battery", common.subclass)
        assertEquals(0.94f, common.confidence, 0.001f)
        assertEquals(0.025, common.estimatedWeight, 0.0001)
        assertEquals("vec_123", common.featureVector)
        assertEquals("/storage/image1.jpg", common.imagePath)
        assertEquals("correct", common.feedback)
        assertNull(common.correctedSubclass)
        assertEquals("Battery|0.94;PCB|0.03", common.topPredictions)
        assertEquals(now, common.timestamp)

        val reconstructedEntity = common.toEntity()

        assertEquals(entity.id, reconstructedEntity.id)
        assertEquals(entity.category, reconstructedEntity.category)
        assertEquals(entity.subclass, reconstructedEntity.subclass)
        assertEquals(entity.confidence, reconstructedEntity.confidence, 0.001f)
        assertEquals(entity.estimatedWeight, reconstructedEntity.estimatedWeight, 0.0001)
        assertEquals(entity.feedback, reconstructedEntity.feedback)
        assertEquals(entity.timestamp, reconstructedEntity.timestamp)
    }
}
