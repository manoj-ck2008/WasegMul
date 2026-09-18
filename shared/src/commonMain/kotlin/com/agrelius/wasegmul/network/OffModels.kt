package com.agrelius.wasegmul.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Lenient nullable-Int decoder for crowd-sourced OFF fields (`packagings_complete`,
 * `number_of_units`) that arrive as JSON numbers, booleans or numeric strings depending
 * on contributor tooling. Accepts `1`, `true`/`false` (→ 1/0) and `"1"`; anything else
 * (including explicit null / missing) decodes to null so [coerceInputValues]-style
 * leniency never throws on real-world payloads. Serializes back as a plain number/null.
 */
object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Int? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return null
        if (element is JsonNull) return null
        if (element !is JsonPrimitive) return null
        if (element.isString) {
            val text = element.contentOrNull?.trim() ?: return null
            text.toIntOrNull()?.let { return it }
            // Numeric strings with surrounding noise ("1 unit") → leading-int parse.
            text.takeWhile { it.isDigit() || it == '-' || it == '+' }
                .toIntOrNull()?.let { return it }
            return when (text.lowercase()) {
                "true", "yes", "y", "1" -> 1
                "false", "no", "n", "0" -> 0
                else -> null
            }
        }
        element.booleanOrNull?.let { return if (it) 1 else 0 }
        return element.intOrNull
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/**
 * Top-level response from Open Food Facts API v3 for product packaging queries.
 */
@Serializable
data class OffProductResponse(
    val code: String = "",
    val status: String? = null,
    val product: OffProductDto? = null
)

/**
 * Waste-relevant product packaging fields from Open Food Facts API v3.
 */
@Serializable
data class OffProductDto(
    val code: String = "",
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @Serializable(with = LenientIntSerializer::class)
    @SerialName("packagings_complete") val packagingsComplete: Int? = 0,
    val packagings: List<OffPackagingComponentDto> = emptyList(),
    @SerialName("packaging_materials_tags") val materialsTags: List<String> = emptyList(),
    @SerialName("packaging_shapes_tags") val shapesTags: List<String> = emptyList(),
    @SerialName("packaging_recycling_tags") val recyclingTags: List<String> = emptyList(),
    /**
     * OFF eco-score grade (A–E). Parsed for wire compat but NOT consumed by waste
     * routing (no verified mapping to disposal actions) — deliberately unused.
     */
    @SerialName("ecoscore_grade") val ecoscoreGrade: String? = null
)

/**
 * A single packaging component (e.g. bottle, cap, box) with material, shape, and recycling data.
 *
 * ## Weight units: GRAMS (not kg)
 * [weight], [weightMeasured] and [weightEstimated] are raw OFF values in **grams**.
 * A caller assuming kg inflates Eco Impact 1000× — always divide by 1000 before storing
 * into [com.agrelius.wasegmul.WasteRecord.estimatedWeight]. See
 * [com.agrelius.wasegmul.PackagingWasteMapper.MAX_COMPONENT_WEIGHT_GRAMS] for the sanity cap.
 */
@Serializable
data class OffPackagingComponentDto(
    val shape: OffTaxonomyItemDto? = null,
    val material: OffTaxonomyItemDto? = null,
    val recycling: OffTaxonomyItemDto? = null,
    @Serializable(with = LenientIntSerializer::class)
    @SerialName("number_of_units") val numberOfUnits: Int? = 1,
    @SerialName("quantity_per_unit") val quantityPerUnit: String? = null,
    /** Declared weight in **grams** (see class KDoc). */
    val weight: Double? = null,
    /** Measured weight in **grams** (see class KDoc). */
    @SerialName("weight_measured") val weightMeasured: Double? = null,
    /** Estimated weight in **grams** (see class KDoc). */
    @SerialName("weight_estimated") val weightEstimated: Double? = null
)

/**
 * Taxonomy item identifier and localized name for shape, material, or recycling instruction.
 *
 * @param localizedName `lc_name` display text in the PROVIDER's locale (may mix languages
 *   across components of one product). Display-only: routing always uses [id].
 */
@Serializable
data class OffTaxonomyItemDto(
    val id: String = "",
    @SerialName("lc_name") val localizedName: String? = null
)
