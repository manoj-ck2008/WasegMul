package com.agrelius.wasegmul.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("packagings_complete") val packagingsComplete: Int? = 0,
    val packagings: List<OffPackagingComponentDto> = emptyList(),
    @SerialName("packaging_materials_tags") val materialsTags: List<String> = emptyList(),
    @SerialName("packaging_shapes_tags") val shapesTags: List<String> = emptyList(),
    @SerialName("packaging_recycling_tags") val recyclingTags: List<String> = emptyList(),
    @SerialName("ecoscore_grade") val ecoscoreGrade: String? = null
)

/**
 * A single packaging component (e.g. bottle, cap, box) with material, shape, and recycling data.
 */
@Serializable
data class OffPackagingComponentDto(
    val shape: OffTaxonomyItemDto? = null,
    val material: OffTaxonomyItemDto? = null,
    val recycling: OffTaxonomyItemDto? = null,
    @SerialName("number_of_units") val numberOfUnits: Int? = 1,
    @SerialName("quantity_per_unit") val quantityPerUnit: String? = null,
    val weight: Double? = null,
    @SerialName("weight_measured") val weightMeasured: Double? = null,
    @SerialName("weight_estimated") val weightEstimated: Double? = null
)

/**
 * Taxonomy item identifier and localized name for shape, material, or recycling instruction.
 */
@Serializable
data class OffTaxonomyItemDto(
    val id: String = "",
    @SerialName("lc_name") val localizedName: String? = null
)
