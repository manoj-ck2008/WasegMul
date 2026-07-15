package com.agrelius.wasegmul

/**
 * Bridge for iOS to access shared logic easily.
 */
object IOSBridge {
    fun getWasteKnowledge(category: String, subclass: String) = 
        WasteKnowledgeBase.getInfo(category, subclass)

    fun arbitrateML(prediction: com.agrelius.wasegmul.data.PredictionResult) = 
        MLArbitrator.arbitrate(prediction)
}
