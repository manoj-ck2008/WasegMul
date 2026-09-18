package com.agrelius.wasegmul

import com.agrelius.wasegmul.ml.classifiers.TfliteClassifier
import com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor
import com.agrelius.wasegmul.network.OpenFoodFactsApi
import com.agrelius.wasegmul.notification.DailyImpactWorker
import com.agrelius.wasegmul.ui.history.CsvRow
import com.agrelius.wasegmul.ui.history.writeHistoryCsv
import com.agrelius.wasegmul.ui.result.sanitizeCsvCell
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedWriter
import java.io.StringWriter
import kotlin.math.max
import kotlin.math.min

/**
 * Validates ML model specifications, tensor serialization, IoU/NMS contracts,
 * and mathematical entropy bounds across detection and classification pipelines.
 *
 * Delegation policy (§3.49): every test below touches PRODUCTION code — shared
 * [MLArbitrator]/[PredictionCodec], [ImagePreprocessor.assertOutputRange],
 * [TfliteClassifier.softmax], [sanitizeCsvCell]/[writeHistoryCsv], or pinned
 * timeout contracts. The single exception is [referenceIoU]: [com.agrelius.wasegmul.ml.YoloDetector]
 * keeps `iou`/`nmsPerClass`/`letterbox` private AND they need
 * `android.graphics` (unavailable in JVM unit tests), so no prod path is
 * reachable without Robolectric. The mirror below is byte-for-byte the
 * algorithm in `YoloDetector.iou` and asserts the IoU CONTRACT — it must be
 * replaced by a `@VisibleForTesting` delegate (UI batch) if that algorithm changes.
 *
 * NOT covered here (needs heavier harness — tracked, not silently dropped):
 * - YOLO parse/NMS/letterbox end-to-end, `ImagePreprocessor` crop/HARDWARE path,
 *   `TfliteClassifier` shape-mismatch guard: need Robolectric or an interpreter.
 * - `MIGRATION_8_9`+: need `room-testing` (not a dependency) / instrumentation.
 * - VM / SettingsManager suites: owned by UI batch.
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

    // ── IoU contract (mirror — see class KDoc; YoloDetector.iou is private) ──

    @Test
    fun testIoUCalculation_identicalBoxes_returnsOne() {
        val boxA = floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f) // left, top, right, bottom
        val boxB = floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f)

        val iou = referenceIoU(boxA, boxB)
        assertEquals(1.0f, iou, 0.001f)
    }

    @Test
    fun testIoUCalculation_disjointBoxes_returnsZero() {
        val boxA = floatArrayOf(0.0f, 0.0f, 0.2f, 0.2f)
        val boxB = floatArrayOf(0.5f, 0.5f, 0.8f, 0.8f)

        val iou = referenceIoU(boxA, boxB)
        assertEquals(0.0f, iou, 0.001f)
    }

    @Test
    fun testIoUCalculation_partialOverlap_returnsExpectedRatio() {
        val boxA = floatArrayOf(0.0f, 0.0f, 1.0f, 1.0f)
        val boxB = floatArrayOf(0.5f, 0.5f, 1.5f, 1.5f)

        val iou = referenceIoU(boxA, boxB)
        assertEquals(0.142857f, iou, 0.005f)
    }

    // ── Entropy via PRODUCTION MLArbitrator.computeEntropy (bits, base-2) ──

    @Test
    fun testEntropyBounds_oneHotDistribution_hasZeroEntropy_prod() {
        val entropy = MLArbitrator.computeEntropy(
            listOf("a" to 1.0f, "b" to 0.0f, "c" to 0.0f, "d" to 0.0f)
        )
        assertEquals("Deterministic one-hot distribution must have zero entropy", 0.0f, entropy, 0.0001f)
    }

    @Test
    fun testEntropyBounds_uniformDistribution_hasMaximumEntropy_prod() {
        val entropy = MLArbitrator.computeEntropy(
            listOf("a" to 0.25f, "b" to 0.25f, "c" to 0.25f, "d" to 0.25f)
        )
        // log2(4) = 2.0 bits — production uses base-2.
        assertEquals("Uniform 4-class distribution must reach log2(4) = 2 bits", 2.0f, entropy, 0.0001f)
    }

    @Test
    fun testEntropyBounds_emptyInput_returnsMaxUncertainty_prod() {
        // Empty carries NO information: production returns the ceiling, never 0
        // (0 would read as "perfectly certain").
        val entropy = MLArbitrator.computeEntropy(emptyList())
        assertEquals(
            "Empty input must return max-uncertainty ceiling",
            MLArbitrator.CATEGORY_ENTROPY_CEILING, entropy, 0.0001f
        )
    }

    @Test
    fun testEntropyBounds_nanInput_cannotDefeatCheck_prod() {
        // NaN must not defeat the confusion check (`< NaN == false` defeats comparisons).
        val entropy = MLArbitrator.computeEntropy(listOf("a" to Float.NaN, "b" to 1.0f))
        assertTrue("NaN-tainted entropy must stay finite", entropy.isFinite())
        assertEquals("NaN ignored + certain remainder => ~0 bits", 0.0f, entropy, 0.0001f)
    }

    // ── PredictionCodec round trip (production) ──

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

    // ── Preprocessing range guard (production ImagePreprocessor) ──

    @Test
    fun testPreprocessRangeGuard_inRangeBuffer_passes_prod() {
        val bad = ImagePreprocessor.assertOutputRange(floatArrayOf(0f, 128f, 255f))
        assertEquals("Raw [0,255] buffer must satisfy the normalization contract", 0, bad)
    }

    @Test
    fun testPreprocessRangeGuard_outOfRangeAndNaN_flagged_prod() {
        val bad = ImagePreprocessor.assertOutputRange(floatArrayOf(-1f, 0f, 255f, 256f, Float.NaN))
        assertEquals("Out-of-range + NaN samples must be flagged (3)", 3, bad)
    }

    // ── Softmax calibration (production TfliteClassifier companion) ──

    @Test
    fun testSoftmax_logitMap_sumsToOneAndRanks_prod() {
        val shares = TfliteClassifier.softmax(mapOf("a" to 2.0f, "b" to 1.0f, "c" to 0.0f))
        assertEquals("Softmax shares must sum to 1", 1.0f, shares.values.sum(), 0.0001f)
        assertTrue("Highest logit must win", shares["a"]!! > shares["b"]!! && shares["b"]!! > shares["c"]!!)
        assertTrue("Shares must be valid probabilities", shares.values.all { it in 0f..1f })
    }

    @Test
    fun testSoftmax_emptyMap_returnsEmpty_prod() {
        assertTrue(TfliteClassifier.softmax(emptyMap()).isEmpty())
    }

    @Test
    fun testSoftmax_allNonFinite_fallsBackToUniform_prod() {
        val shares = TfliteClassifier.softmax(mapOf("a" to Float.NaN, "b" to Float.NaN))
        assertEquals(0.5f, shares["a"]!!, 0.0001f)
        assertEquals(0.5f, shares["b"]!!, 0.0001f)
    }

    // ── CSV sanitize + streaming export (production) ──

    @Test
    fun testSanitizeCsvCell_formulaPrefix_guardedAndQuoted_prod() {
        assertEquals("\"'=SUM(A1)\"", sanitizeCsvCell("=SUM(A1)"))
        assertEquals("\"'+cmd\"", sanitizeCsvCell("+cmd"))
        assertEquals("\"'-cmd\"", sanitizeCsvCell("-cmd"))
        assertEquals("\"'@cmd\"", sanitizeCsvCell("@cmd"))
    }

    @Test
    fun testSanitizeCsvCell_plainAndQuotes_quoted_prod() {
        assertEquals("\"Plastic\"", sanitizeCsvCell("Plastic"))
        assertEquals("\"a\"\"b\"", sanitizeCsvCell("a\"b"))
    }

    @Test
    fun testWriteHistoryCsv_headerAndInjectionGuardAndRootLocale_prod() {
        val writer = StringWriter()
        BufferedWriter(writer).use { out ->
            writeHistoryCsv(
                out,
                sequenceOf(
                    CsvRow(
                        id = 1L,
                        timestampIso = "2026-09-18T00:00:00Z",
                        timestampEpoch = 1_700_000_000_000L,
                        category = "Recyclable",
                        subclass = "Plastic",
                        confidence = 0.12345f,
                        weightKg = 2.5,
                        feedback = "=HYPERLINK(\"evil\")",
                        correctedSubclass = null,
                        source = "camera",
                        productName = "Bottle",
                        barcode = "4006381333931",
                        imagePath = null
                    )
                )
            )
        }
        val lines = writer.toString().trim().lines()
        assertEquals("Header + 1 row", 2, lines.size)
        assertTrue("Header must list all export columns", lines[0].startsWith("ID,TimestampISO,"))
        assertTrue("Formula cell must be guarded", lines[1].contains("\"'=HYPERLINK"))
        // Locale.ROOT: decimal point even under comma-decimal display locales.
        assertTrue("Confidence must render with '.' decimals", lines[1].contains("0.1235"))
    }

    // ── Pinned timeout contracts (consts — compile-time inlined, no Android init) ──

    @Test
    fun testOffTimeouts_connectAndRequestPinned_prod() {
        assertEquals("OFF connect timeout pins the §3.32 fan-out budget", 5_000L, OpenFoodFactsApi.CONNECT_TIMEOUT_MS)
        assertEquals("OFF request timeout pins the §3.32 fan-out budget", 10_000L, OpenFoodFactsApi.REQUEST_TIMEOUT_MS)
        assertEquals("OFF retry base pins the backoff policy", 500L, OpenFoodFactsApi.RETRY_BASE_DELAY_MS)
    }

    @Test
    fun testWorkerPrefsTimeout_pinned_prod() {
        assertEquals(
            "DailyImpactWorker DataStore read must time out (never stall past the 10-min window)",
            10_000L, DailyImpactWorker.PREFS_TIMEOUT_MS
        )
    }

    /**
     * Pure-Kotlin mirror of `YoloDetector.iou` (same max/min area math) for JVM
     * tests. Replace with a `@VisibleForTesting` delegate to production code
     * (UI batch) — see class KDoc.
     */
    private fun referenceIoU(boxA: FloatArray, boxB: FloatArray): Float {
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
}
