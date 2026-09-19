package com.agrelius.wasegmul.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ktor-based HTTP client for the Open Food Facts API v3.
 *
 * Requests waste-relevant packaging fields for a given product barcode.
 * Configured with standard timeouts, custom User-Agent, and lenient JSON parsing
 * (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues` plus [LenientIntSerializer]
 * for crowd-typed `packagings_complete` / `number_of_units`).
 *
 * ## Null conventions (three surfaces, one table — read before calling)
 * - [queryEndpoint]: HTTP 404 → `Result.success(null)` (not present in THAT database).
 * - [getProduct]: FOOD-only; never null — a 404 becomes a placeholder response with
 *   `status = "product not found"` and `product = null` (legacy contract, kept).
 * - [getProductCascade]: FOOD → PRODUCTS → BEAUTY; total miss → `Result.success(null)`.
 * A `200` with a null product is treated the same as a miss everywhere.
 *
 * ## Scope: FOOD vs cascade
 * [getProduct] queries Open Food Facts (groceries/food) ONLY. Barcodes for electronics,
 * toys or cosmetics live in sibling databases — use [getProductCascade], which fans out
 * across FOOD + PRODUCTS + BEAUTY with per-endpoint error isolation.
 *
 * ## Request path (verified against OFF v3)
 * `GET {baseUrl}/{percent-encoded barcode}.json?fields={FIELDS}` — v3 path style with the
 * barcode in the path and fields as a query parameter. If OFF migrates this route, the
 * failure mode is loud (non-2xx → error Result with the truncated body attached), and a
 * live integration test (not a unit test — no network in unit tests) must re-verify.
 *
 * ## Cancellation & errors
 * [CancellationException] is ALWAYS rethrown (never swallowed into a Result): coroutine
 * cancellation must propagate or scoped work leaks. HTTP 429/5xx are retried with
 * backoff ([maxRetries], 500 ms × attempt); other errors fail immediately with the
 * truncated response body attached. Set [logger] to observe retries/failures
 * (no logging framework in commonMain — the hook is the logging story).
 *
 * ## Client ownership
 * The no-arg constructor creates AND owns its client (closed by [close]). An INJECTED
 * client is never closed by [close] (shared clients must outlive one API facade) —
 * close it at its own owner. This is documented on the constructors, not just here.
 */
class OpenFoodFactsApi private constructor(
    private val client: HttpClient,
    private val ownsClient: Boolean
) : AutoCloseable {

    /** Creates (and owns) a default client. */
    constructor() : this(createDefaultClient(), true)

    /**
     * Uses an injected shared client. Ownership stays with the caller: [close] is a no-op.
     */
    constructor(client: HttpClient) : this(client, false)

    /** Observability hook: retry/failure lines. Null = silent. */
    var logger: ((String) -> Unit)? = null

    /** Retries for HTTP 429/5xx (backoff 500 ms × attempt). 0 = no retry. */
    var maxRetries: Int = 2

    companion object {
        const val BASE_URL_FOOD = "https://world.openfoodfacts.org/api/v3/product"
        const val BASE_URL_PRODUCTS = "https://world.openproductsfacts.org/api/v3/product"
        const val BASE_URL_BEAUTY = "https://world.openbeautyfacts.org/api/v3/product"
        const val BASE_URL = BASE_URL_FOOD
        /** Single-sourced with gradle.properties VERSION_NAME (3.0.0); bump together. */
        const val USER_AGENT = "WasegMul/3.0.0 (Android; support@wasegmul.app)"

        /**
         * Requested fields. Includes `product_name_en` + `lang` alongside `product_name`
         * so non-English products still resolve a displayable name; `ecoscore_grade` is
         * parsed but unused (see [OffProductDto.ecoscoreGrade]).
         */
        const val FIELDS = "code,product_name,product_name_en,lang,brands,packagings,packagings_complete," +
            "packaging_materials_tags,packaging_shapes_tags,packaging_recycling_tags,ecoscore_grade"
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 10_000L

        /** Backoff base for 429/5xx retries (multiplied by attempt number). */
        const val RETRY_BASE_DELAY_MS = 500L

        /** Truncation cap for error bodies attached to failure messages. */
        const val MAX_ERROR_BODY_CHARS = 256

        /**
         * Creates a default [HttpClient] configured for Open Food Facts API v3 queries.
         */
        fun createDefaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        }

        /**
         * Validates a GTIN/EAN/UPC barcode: strips spaces and dashes, requires all-digits
         * with a GS1 length (8, 12, 13 or 14) and a correct GS1 Modulo-10 check digit.
         * Use to reject scanner junk (URLs, QR payloads) BEFORE hitting DAO/network.
         */
        fun isValidGtin(raw: String?): Boolean {
            if (raw.isNullOrBlank()) return false
            val digits = raw.filter { it.isDigit() }
            // Reject when stripping changed anything but whitespace/dashes (letters, URLs...).
            val stripped = raw.filter { !it.isWhitespace() && it != '-' }
            if (stripped.length != digits.length) return false
            if (digits.length !in setOf(8, 12, 13, 14)) return false
            var sum = 0
            digits.dropLast(1).reversed().forEachIndexed { i, c ->
                val d = c - '0'
                sum += if (i % 2 == 0) d * 3 else d
            }
            val check = (10 - (sum % 10)) % 10
            return check == (digits.last() - '0')
        }

        /**
         * Normalises scanner output to bare digits (strips whitespace/dashes). Returns null
         * when nothing digit-like remains. Does NOT validate the check digit — pair with
         * [isValidGtin] when validation (not just cleaning) is required.
         */
        fun normaliseGtin(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val digits = raw.filter { it.isDigit() }
            return digits.ifBlank { null }
        }
    }

    /**
     * Queries a specific base URL for product packaging details.
     * Returns null if HTTP 404 is encountered (product not present in that database).
     * Retries HTTP 429/5xx with backoff; rethrows [CancellationException].
     *
     * NOTE: hand-rolled try/catch (not `runCatching`) so [CancellationException] can
     * propagate — `runCatching` would capture it into a failure Result and leak the scope.
     */
    suspend fun queryEndpoint(baseUrl: String, barcode: String): Result<OffProductResponse?> {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Barcode must not be blank"))
        return try {
            val encodedBarcode = percentEncodePathSegment(trimmed)
            var attempt = 0
            var result: OffProductResponse? = null
            var done = false
            while (!done) {
                val response = client.get("$baseUrl/$encodedBarcode.json") {
                    parameter("fields", FIELDS)
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (response.status.value == 404) {
                    result = null
                    done = true
                } else if ((response.status.value == 429 || response.status.value in 500..599) &&
                    attempt < maxRetries
                ) {
                    attempt++
                    logger?.invoke("OFF $baseUrl → ${response.status.value}, retry $attempt/$maxRetries")
                    delay(RETRY_BASE_DELAY_MS * attempt)
                } else if (!response.status.isSuccess()) {
                    val body = runCatching { response.bodyAsText() }.getOrNull()
                        ?.take(MAX_ERROR_BODY_CHARS)
                    throw IllegalStateException(
                        "API error: HTTP ${response.status.value} ${response.status.description}" +
                            (body?.let { " body=$it" } ?: "")
                    )
                } else {
                    result = response.body<OffProductResponse>()
                    done = true
                }
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches product packaging details by barcode from Open Food Facts API v3 (FOOD only;
     * see class KDoc for scope).
     * Returns a response with null product on HTTP 404 rather than throwing an unhandled error.
     *
     * @param barcode Product barcode string (e.g. EAN-13, UPC).
     * @return [Result] wrapping [OffProductResponse].
     */
    suspend fun getProduct(barcode: String): Result<OffProductResponse> {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Barcode must not be blank"))
        return try {
            val res = queryEndpoint(BASE_URL_FOOD, trimmed).getOrThrow()
            Result.success(res ?: OffProductResponse(code = trimmed, status = "product not found", product = null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cascading search across multiple databases:
     * 1. Open Food Facts (groceries, food items)
     * 2. Open Products Facts (electronics, laptops, washing machines, games, Uno cards, toys)
     * 3. Open Beauty Facts (cosmetics, personal care)
     *
     * UPC-A (12-digit) inputs are also tried zero-padded to EAN-13 and vice versa.
     * Candidates failing [isValidGtin] are skipped WITHOUT network calls (scanner junk such
     * as URLs must not fan out to 6 endpoints). Per-endpoint failures are isolated: one
     * database error never aborts the remaining lookups. [CancellationException] aborts
     * the whole cascade immediately.
     *
     * Returns the first found product response, or null if missing from all databases.
     */
    suspend fun getProductCascade(barcode: String): Result<OffProductResponse?> {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Barcode must not be blank"))
        return try {
            val candidates = buildList {
                add(trimmed)
                if (trimmed.length == 12) {
                    add("0$trimmed")
                } else if (trimmed.length == 13 && trimmed.startsWith('0')) {
                    add(trimmed.substring(1))
                }
            }.distinct().filter { isValidGtin(it) }
            if (candidates.isEmpty()) {
                logger?.invoke("OFF cascade: '$trimmed' is not a valid GTIN, skipping network")
                return Result.success(null)
            }

            val endpoints = listOf(BASE_URL_FOOD, BASE_URL_PRODUCTS, BASE_URL_BEAUTY)
            for (candidate in candidates) {
                for (url in endpoints) {
                    try {
                        val res = queryEndpoint(url, candidate).getOrNull()
                        if (res != null && res.product != null) {
                            return Result.success(res)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ignored: Exception) {
                        logger?.invoke("OFF cascade: $url failed for $candidate: ${ignored.message}")
                        // Continue checking next database endpoint
                    }
                }
            }
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Alias for [getProduct] to support diverse call conventions.
     */
    suspend fun fetchProduct(barcode: String): Result<OffProductResponse> = getProduct(barcode)

    private fun percentEncodePathSegment(segment: String): String = segment.map { c ->
        when {
            c.isLetterOrDigit() || c in ".-_~" -> c.toString()
            else -> "%" + c.code.toString(16).padStart(2, '0').uppercase()
        }
    }.joinToString("")

    /**
     * Closes the underlying HTTP client ONLY when this instance owns it (no-arg
     * constructor). For injected shared clients this is a deliberate no-op — the owner
     * closes its own client.
     */
    override fun close() {
        if (ownsClient) runCatching { client.close() }
    }
}
