package com.agrelius.wasegmul

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Validates ML model specifications, tensor serialization, IoU calculation,
 * and mathematical entropy bounds across detection and classification pipelines.
 */
class ModelBenchmarkTest {

    private val yolo15WasteClasses = listOf(
        "battery",
        "bottle",
        "can",
        "cardboard",
        "cigarette",
        "cup",
        "electronic",
        "food_waste",
        "glass_container",
        "metal",
        "paper",
        "plastic_bag",
        "plastic_container",
        "plastic_wrapper",
        "textile"
    )

    private val all30Subclasses = listOf(
        "Air-Conditioner", "Battery", "Cardboard", "Electronic Component",
        "Electronic Device", "Glass", "Keyboard", "Laptop", "Metal",
        "Microwave", "Miscellaneous Trash", "Mobile", "Mouse", "Organic",
        "PCB", "Paper", "Plastic", "Player", "Printer", "Refrigerator",
        "Television", "Textile Trash", "Washing Machine", "automobile wastes",
        "clothing", "disposable_plastic_cutlery", "light bulbs", "shoes",
        "styrofoam_cups", "styrofoam_food_containers"
    )

    @Test
    fun testYolo15Classes_exactCountAndIntegrity() {
        assertEquals("YOLO visual detection classes must count 15", 15, yolo15WasteClasses.size)
        val unique = yolo15WasteClasses.toSet()
        assertEquals("All 15 YOLO classes must be distinct", 15, unique.size)
    }

    @Test
    fun testAll30Subclasses_exactCountAndIntegrity() {
        assertEquals("Semantic subclasses must count 30", 30, all30Subclasses.size)
        val unique = all30Subclasses.toSet()
        assertEquals("All 30 subclasses must be distinct", 30, unique.size)
    }

    @Test
    fun testIoUCalculation_identicalBoxes_returnsOne() {
        val boxA = floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f) // left, top, right, bottom
        val boxB = floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f)

        val iou = calculateIoU(boxA, boxB)
        assertEquals(1.0f, iou, 0.001f)
    }

    @Test
    fun testIoUCalculation_disjointBoxes_returnsZero() {
        val boxA = floatArrayOf(0.0f, 0.0f, 0.2f, 0.2f)
        val boxB = floatArrayOf(0.5f, 0.5f, 0.8f, 0.8f)

        val iou = calculateIoU(boxA, boxB)
        assertEquals(0.0f, iou, 0.001f)
    }

    @Test
    fun testIoUCalculation_partialOverlap_returnsExpectedRatio() {
        val boxA = floatArrayOf(0.0f, 0.0f, 1.0f, 1.0f)
        val boxB = floatArrayOf(0.5f, 0.5f, 1.5f, 1.5f)

        val iou = calculateIoU(boxA, boxB)
        assertEquals(0.142857f, iou, 0.005f)
    }

    @Test
    fun testPredictionCodec_roundTripPrecision() {
        val original = all30Subclasses.mapIndexed { index, subclass ->
            subclass to (index + 1) / 465.0f
        }
        val encoded = PredictionCodec.encode(original)
        val decoded = PredictionCodec.decode(encoded)

        assertNotNull("Decoded list must not be null", decoded)
        assertEquals("Decoded list length must match original", original.size, decoded.size)

        for (i in original.indices) {
            assertEquals("Label at index $i must match", original[i].first, decoded[i].first)
            assertEquals(
                "Value at index $i must match within precision limit",
                original[i].second,
                decoded[i].second,
                0.001f
            )
        }
    }

    @Test
    fun testEntropyBounds_oneHotDistribution_hasZeroEntropy() {
        val probs = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)
        val entropy = calculateShannonEntropy(probs)
        assertEquals("Deterministic one-hot distribution must have zero entropy", 0.0f, entropy, 0.0001f)
    }

    @Test
    fun testEntropyBounds_uniformDistribution_hasMaximumEntropy() {
        val n = 4
        val probs = FloatArray(n) { 1.0f / n }
        val entropy = calculateShannonEntropy(probs)
        val expectedMaxEntropy = ln(n.toDouble()).toFloat()

        assertEquals(
            "Uniform 4-class distribution must achieve theoretical maximum entropy ln(4)",
            expectedMaxEntropy,
            entropy,
            0.0001f
        )
    }

    private fun calculateIoU(boxA: FloatArray, boxB: FloatArray): Float {
        val xA = max(boxA[0], boxB[0])
        val yA = max(boxA[1], boxB[1])
        val xB = min(boxA[2], boxB[2])
        val yB = min(boxA[3], boxB[3])

        val interWidth = max(0.0f, xB - xA)
        val interHeight = max(0.0f, yB - yA)
        val interArea = interWidth * interHeight

        val boxAArea = (boxA[2] - boxA[0]) * (boxA[3] - boxA[1])
        val boxBArea = (boxB[2] - boxB[0]) * (boxB[3] - boxB[1])

        val unionArea = boxAArea + boxBArea - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun calculateShannonEntropy(probabilities: FloatArray): Float {
        var entropy = 0.0f
        for (p in probabilities) {
            if (p > 1e-7f) {
                entropy -= p * ln(p.toDouble()).toFloat()
            }
        }
        return entropy
    }
}
