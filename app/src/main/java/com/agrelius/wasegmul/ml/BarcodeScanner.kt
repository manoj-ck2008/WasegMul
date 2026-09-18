package com.agrelius.wasegmul.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a successfully scanned barcode result.
 *
 * @property rawValue The raw string value encoded within the barcode symbol.
 * @property format The format identifier defined in [Barcode] (e.g., [Barcode.FORMAT_EAN_13]).
 * @property displayValue A human-readable display string formatted by ML Kit.
 * @property frameBitmap Captured camera frame bitmap when the barcode was detected, used for visual ML fallback.
 */
data class BarcodeResult(
    val rawValue: String,
    val format: Int,
    val displayValue: String,
    val frameBitmap: Bitmap? = null
)

/**
 * Offline barcode scanner wrapper leveraging Google ML Kit Barcode Scanning for the WasegMul waste segregation system.
 *
 * ### Offline Architecture and Privacy
 * Google ML Kit Barcode Scanning executes entirely on-device using local computer vision
 * models bundled with the application or supplied via Google Play Services. No camera
 * frames or scanned barcode contents are transmitted over network connections during the
 * scanning workflow.
 */
class BarcodeScanner : Closeable {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()

    private val scannerDelegate = lazy {
        BarcodeScanning.getClient(options)
    }

    private val scanner by scannerDelegate

    private val isProcessing = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    @Volatile
    private var lastScanTimeMs: Long = 0L

    /**
     * Scans the provided [imageProxy] for supported barcodes.
     *
     * Skips scanning if:
     * - A scan operation is already in progress ([isProcessing] guard).
     * - The scanner is within the debounce cooldown period ([DEBOUNCE_DELAY_MS] from
     *   last successful scan).
     * - The scanner has been closed.
     *
     * ### Throttling (documented contract, §3.36)
     * Three independent gates: (1) [isProcessing] CAS drops overlapping frames so a
     * slow ML Kit call cannot queue work; (2) [DEBOUNCE_DELAY_MS] cooldown suppresses
     * repeat reads of the same code; (3) [SCAN_TIMEOUT_MS] bounds `await()` so a
     * wedged ML Kit task cannot hold the caller's `ImageProxy` open forever (proxy
     * close + executor lifecycle remain the CALLER's job — `BarcodeScanScreen`
     * closes in `finally` after dispatch; verify there on CameraX changes, §3.43).
     *
     * @param imageProxy The image frame to analyze. NOT closed here.
     * @return The first detected [BarcodeResult], or null if no supported barcode is found,
     *         if an error occurs, or if throttled by concurrency or cooldown.
     */
    suspend fun scan(imageProxy: ImageProxy): BarcodeResult? {
        val mediaImage = imageProxy.image ?: return null
        if (isClosed.get()) {
            return null
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastScanTimeMs
        if (lastScanTimeMs > 0L && elapsed in 0 until DEBOUNCE_DELAY_MS) {
            return null
        }

        if (!isProcessing.compareAndSet(false, true)) {
            return null
        }

        return try {
            if (isClosed.get()) return null

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            // Bounded await (§3.36): without this a stuck ML Kit task holds the
            // ImageProxy open and stalls the CameraX pipeline (STRATEGY_KEEP_ONLY_LATEST
            // cannot help while the analyzer never returns).
            val barcodes = try {
                withTimeout(SCAN_TIMEOUT_MS) { scanner.process(inputImage).await() }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Barcode scan timed out after ${SCAN_TIMEOUT_MS}ms; frame dropped")
                return null
            }
            val firstBarcode = barcodes.firstOrNull() ?: return null

            val rawValue = firstBarcode.rawValue ?: firstBarcode.displayValue ?: return null
            val displayValue = firstBarcode.displayValue ?: rawValue
            if (isJunkPayload(rawValue)) {
                Log.d(TAG, "Rejecting junk barcode payload pre-resolve")
                return null
            }
            val resolvedCode = extractGtinFromUrl(rawValue)

            // Extract frame bitmap for visual ML fallback only when a barcode is actually detected.
            // toBitmap() ignores sensor rotation, so rotate to upright here (§3.36) —
            // otherwise the Tier-4 fallback classifies a sideways frame.
            val frameBitmap = try {
                val upright = imageProxy.toBitmap()
                if (rotationDegrees % 360 != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated =
                        Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
                    if (rotated !== upright) upright.recycle()
                    rotated
                } else {
                    upright
                }
            } catch (e: OutOfMemoryError) {
                // Full-res frame too large: drop the visual fallback, keep the barcode.
                Log.w(TAG, "Frame bitmap OOM; continuing without visual fallback")
                null
            } catch (e: Exception) {
                null
            }

            lastScanTimeMs = System.currentTimeMillis()

            BarcodeResult(
                rawValue = resolvedCode,
                format = firstBarcode.format,
                displayValue = displayValue,
                frameBitmap = frameBitmap
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Barcode scan OOM; frame dropped")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Barcode scan processing error: ${e.message}")
            null
        } finally {
            isProcessing.set(false)
        }
    }

    /**
     * Resets the debounce cooldown timestamp, allowing the next scan attempt to proceed immediately.
     */
    fun resetCooldown() {
        lastScanTimeMs = 0L
    }

    /**
     * Validates an EAN-13 barcode string using the standard GS1 Modulo-10 checksum algorithm.
     *
     * An EAN-13 barcode consists of 12 data digits followed by 1 check digit. The check digit
     * is calculated using alternating weights: odd positions (1, 3, 5, 7, 9, 11) have weight 1,
     * and even positions (2, 4, 6, 8, 10, 12) have weight 3.
     *
     * @param code The string to validate.
     * @return True if [code] is a 13-digit numeric string with a valid Modulo-10 check digit.
     */
    fun isValidEan13(code: String): Boolean = Companion.isValidEan13(code)

    /**
     * Closes the underlying ML Kit barcode scanner client and releases associated native resources.
     */
    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            if (scannerDelegate.isInitialized()) {
                try {
                    scanner.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing barcode scanner client: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BarcodeScanner"

        /**
         * Debounce cooldown in milliseconds following a successful barcode scan.
         */
        const val DEBOUNCE_DELAY_MS = 2000L

        /**
         * Upper bound for one ML Kit `process()` call. Exceeding it drops the frame
         * (returns null) so the CameraX pipeline never stalls on a wedged task.
         */
        const val SCAN_TIMEOUT_MS = 8_000L

        /**
         * Validates an EAN-13 barcode string using the standard GS1 Modulo-10 checksum algorithm.
         *
         * @param code The string to validate.
         * @return True if [code] is a 13-digit numeric string with a valid Modulo-10 check digit.
         */
        fun isValidEan13(code: String): Boolean {
            if (code.length != 13 || !code.all { it.isDigit() }) {
                return false
            }

            var sum = 0
            for (i in 0 until 12) {
                val digit = code[i] - '0'
                sum += if (i % 2 == 0) digit else digit * 3
            }

            val checkDigit = (10 - (sum % 10)) % 10
            val actualCheckDigit = code[12] - '0'
            return checkDigit == actualCheckDigit
        }

        /**
         * Extracts a numeric GTIN or barcode from a GS1 Digital Link or URL if present.
         * For example, "https://id.gs1.org/01/08435111111112" returns "08435111111112".
         *
         * Covers: `/01/…`, `/(01)…`, `/gtin/…`, `/product/…`, `?gtin=…` / `?…gtin=…`,
         * slash-less `…/01<14 digits>`, bare `01<14 digits>` element strings (no slash
         * at all, e.g. QR payloads), and whitespace-padded QR payloads (trimmed
         * first). Returns the trimmed input unchanged when no GS1 pattern matches.
         */
        fun extractGtinFromUrl(raw: String): String {
            val trimmed = raw.trim()
            val gs1Pattern = Regex("""(?:/(?:01|gtin|product)/|\(01\))(\d{8,14})""")
            gs1Pattern.find(trimmed)?.let { return it.groupValues[1] }
            // Query-param form: ?gtin=… or &gtin=…
            Regex("""[?&]gtin=(\d{8,14})""").find(trimmed)?.let { return it.groupValues[1] }
            // Slash-less GS1 element string tail: …/01<digits> without inner slash.
            Regex("""/01(\d{14})(?:/|$)""").find(trimmed)?.let { return it.groupValues[1] }
            // Bare element string: 01 + 14 digits not embedded in a longer digit run.
            Regex("""(?<!\d)01(\d{14})(?!\d)""").find(trimmed)?.let { return it.groupValues[1] }
            return trimmed
        }

        /**
         * Junk rejection pre-OFF-resolve (§3.36): drops payloads that must never reach
         * Tier-1/3 lookup or the Room cache — blanks, `code_<timestamp>` fabricated
         * keys, non-GTIN URLs, and payloads with no digits at all.
         */
        fun isJunkPayload(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return true
            if (trimmed.startsWith("code_")) return true
            if (!trimmed.any { it.isDigit() }) return true
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                // URL: only worth resolving when a GS1 pattern extracts a GTIN.
                return extractGtinFromUrl(trimmed) == trimmed
            }
            return false
        }
    }
}
