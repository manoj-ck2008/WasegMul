package com.agrelius.wasegmul

/**
 * Bridge for iOS to access shared logic easily.
 */
object IOSBridge {
    fun getWasteKnowledge(category: String, subclass: String): WasteInfo {
        return WasteKnowledgeBase.getInfo(category, subclass)
    }

    fun arbitrateML(prediction: PredictionResult): PredictionResult {
        return MLArbitrator.arbitrate(prediction)
    }
}
