package com.agrelius.wasegmul.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor-based HTTP client for the Open Food Facts API v3.
 *
 * Requests waste-relevant packaging fields for a given product barcode.
 * Configured with standard timeouts, custom User-Agent, and lenient JSON parsing.
 */
class OpenFoodFactsApi(
    private val client: HttpClient = createDefaultClient()
) : AutoCloseable {

    companion object {
        const val BASE_URL_FOOD = "https://world.openfoodfacts.org/api/v3/product"
        const val BASE_URL_PRODUCTS = "https://world.openproductsfacts.org/api/v3/product"
        const val BASE_URL_BEAUTY = "https://world.openbeautyfacts.org/api/v3/product"
        const val BASE_URL = BASE_URL_FOOD
        const val USER_AGENT = "WasegMul/1.1.0 (Android; support@wasegmul.app)"
        const val FIELDS = "code,product_name,brands,packagings,packagings_complete," +
            "packaging_materials_tags,packaging_shapes_tags,packaging_recycling_tags,ecoscore_grade"
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 10_000L

        /**
         * Creates a default [HttpClient] configured for Open Food Facts API v3 queries.
         */
        fun createDefaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
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
    }

    /**
     * Queries a specific base URL for product packaging details.
     * Returns null if HTTP 404 is encountered (product not present in that database).
     */
    suspend fun queryEndpoint(baseUrl: String, barcode: String): Result<OffProductResponse?> = runCatching {
        val trimmed = barcode.trim()
        require(trimmed.isNotEmpty()) { "Barcode must not be blank" }

        val encodedBarcode = trimmed.map { c ->
            when {
                c.isLetterOrDigit() || c in ".-_~" -> c.toString()
                else -> "%" + c.code.toString(16).padStart(2, '0').uppercase()
            }
        }.joinToString("")

        val response = client.get("$baseUrl/$encodedBarcode.json") {
            parameter("fields", FIELDS)
            header(HttpHeaders.UserAgent, USER_AGENT)
        }

        if (response.status.value == 404) {
            return@runCatching null
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "API error: HTTP ${response.status.value} ${response.status.description}"
            )
        }

        response.body<OffProductResponse>()
    }

    /**
     * Fetches product packaging details by barcode from Open Food Facts API v3.
     * Returns a response with null product on HTTP 404 rather than throwing an unhandled error.
     *
     * @param barcode Product barcode string (e.g. EAN-13, UPC).
     * @return [Result] wrapping [OffProductResponse].
     */
    suspend fun getProduct(barcode: String): Result<OffProductResponse> = runCatching {
        val trimmed = barcode.trim()
        require(trimmed.isNotEmpty()) { "Barcode must not be blank" }

        val res = queryEndpoint(BASE_URL_FOOD, trimmed).getOrThrow()
        res ?: OffProductResponse(code = trimmed, status = "product not found", product = null)
    }

    /**
     * Cascading search across multiple databases:
     * 1. Open Food Facts (groceries, food items)
     * 2. Open Products Facts (electronics, laptops, washing machines, games, Uno cards, toys)
     * 3. Open Beauty Facts (cosmetics, personal care)
     *
     * Returns the first found product response, or null if missing from all databases.
     */
    suspend fun getProductCascade(barcode: String): Result<OffProductResponse?> = runCatching {
        val trimmed = barcode.trim()
        require(trimmed.isNotEmpty()) { "Barcode must not be blank" }

        val candidates = buildList {
            add(trimmed)
            if (trimmed.length == 12) {
                add("0$trimmed")
            } else if (trimmed.length == 13 && trimmed.startsWith('0')) {
                add(trimmed.substring(1))
            }
        }.distinct()

        val endpoints = listOf(BASE_URL_FOOD, BASE_URL_PRODUCTS, BASE_URL_BEAUTY)
        for (candidate in candidates) {
            for (url in endpoints) {
                try {
                    val res = queryEndpoint(url, candidate).getOrNull()
                    if (res != null && res.product != null) {
                        return@runCatching res
                    }
                } catch (ignored: Exception) {
                    // Continue checking next database endpoint
                }
            }
        }
        null
    }

    /**
     * Alias for [getProduct] to support diverse call conventions.
     */
    suspend fun fetchProduct(barcode: String): Result<OffProductResponse> = getProduct(barcode)

    /**
     * Closes the underlying HTTP client and releases network resources.
     */
    override fun close() {
        client.close()
    }
}
