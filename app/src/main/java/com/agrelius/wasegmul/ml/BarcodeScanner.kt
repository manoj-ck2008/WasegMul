package com.agrelius.wasegmul.ml

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
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
     * - The scanner is within the debounce cooldown period (2000ms from last successful scan).
     * - The scanner has been closed.
     *
     * @param imageProxy The image frame to analyze.
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

            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val barcodes = scanner.process(inputImage).await()
            val firstBarcode = barcodes.firstOrNull() ?: return null

            val rawValue = firstBarcode.rawValue ?: firstBarcode.displayValue ?: return null
            val displayValue = firstBarcode.displayValue ?: rawValue
            val resolvedCode = extractGtinFromUrl(rawValue)

            // Extract frame bitmap for visual ML fallback only when a barcode is actually detected
            val frameBitmap = try {
                imageProxy.toBitmap()
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
         */
        fun extractGtinFromUrl(raw: String): String {
            val trimmed = raw.trim()
            val gs1Pattern = Regex("""(?:/(?:01|gtin|product)/|\(01\))(\d{8,14})""")
            val match = gs1Pattern.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
            return trimmed
        }
    }
}
