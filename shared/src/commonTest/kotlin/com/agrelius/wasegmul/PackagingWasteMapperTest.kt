package com.agrelius.wasegmul

import com.agrelius.wasegmul.network.OffPackagingComponentDto
import com.agrelius.wasegmul.network.OffProductDto
import com.agrelius.wasegmul.network.OffProductResponse
import com.agrelius.wasegmul.network.OffTaxonomyItemDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackagingWasteMapperTest {

    @Test
    fun testMaterialMappingContainsAtLeast25Entries() {
        assertTrue(
            PackagingWasteMapper.MATERIAL_MAPPING.size >= 25,
            "MATERIAL_MAPPING must contain at least 25 entries (actual: ${PackagingWasteMapper.MATERIAL_MAPPING.size})"
        )
    }

    @Test
    fun testMaterialMappingCoversRequiredCategories() {
        val mapping = PackagingWasteMapper.MATERIAL_MAPPING

        // Polymer codes 1-7
        assertEquals(Pair("Recyclable", "Plastic"), mapping["en:pet-1"])
        assertEquals(Pair("Recyclable", "Plastic"), mapping["en:hdpe-2"])
        assertEquals(Pair("Trash", "Plastic"), mapping["en:pvc-3"])
        assertEquals(Pair("Recyclable", "Plastic"), mapping["en:ldpe-4"])
        assertEquals(Pair("Recyclable", "Plastic"), mapping["en:pp-5"])
        assertEquals(Pair("Trash", "Plastic"), mapping["en:ps-6"])
        assertEquals(Pair("Trash", "Plastic"), mapping["en:other-plastics-7"])

        // Metals
        assertEquals(Pair("Recyclable", "Metal"), mapping["en:aluminium"])
        assertEquals(Pair("Recyclable", "Metal"), mapping["en:steel"])

        // Glass
        assertEquals(Pair("Recyclable", "Glass"), mapping["en:clear-glass"])
        assertEquals(Pair("Recyclable", "Glass"), mapping["en:green-glass"])

        // Paper / Cardboard
        assertEquals(Pair("Recyclable", "Paper"), mapping["en:paper"])
        assertEquals(Pair("Recyclable", "Cardboard"), mapping["en:cardboard"])
        assertEquals(Pair("Recyclable", "Cardboard"), mapping["en:corrugated-cardboard"])

        // Composites
        assertEquals(Pair("Recyclable", "Cardboard"), mapping["en:tetra-pak"])
        assertEquals(Pair("Trash", "Miscellaneous Trash"), mapping["en:composite-material"])

        // Natural & Bioplastics
        assertEquals(Pair("Organic", "Organic"), mapping["en:wood"])
        assertEquals(Pair("Organic", "Organic"), mapping["en:cork"])
        assertEquals(Pair("Organic", "Organic"), mapping["en:bioplastic"])
        assertEquals(Pair("Organic", "Organic"), mapping["en:pla"])
    }

    @Test
    fun testShapeFallbackMappings() {
        val fallback = PackagingWasteMapper.SHAPE_FALLBACK

        assertEquals(Pair("Recyclable", "Plastic"), fallback["en:bottle"])
        assertEquals(Pair("Recyclable", "Metal"), fallback["en:can"])
        assertEquals(Pair("Recyclable", "Cardboard"), fallback["en:box"])
        assertEquals(Pair("Recyclable", "Glass"), fallback["en:jar"])
        assertEquals(Pair("Trash", "Plastic"), fallback["en:pouch"])
    }

    @Test
    fun testHumanizeTag() {
        assertEquals("PET", PackagingWasteMapper.humanizeTag("en:pet-1"))
        assertEquals("Clear Glass", PackagingWasteMapper.humanizeTag("en:clear-glass"))
        assertEquals("HDPE", PackagingWasteMapper.humanizeTag("en:hdpe-2"))
        assertEquals("Corrugated Cardboard", PackagingWasteMapper.humanizeTag("en:corrugated-cardboard"))
        assertEquals("Tetra Pak", PackagingWasteMapper.humanizeTag("en:tetra-pak"))
        assertEquals("Bottle", PackagingWasteMapper.humanizeTag("en:bottle"))
        assertEquals("Can", PackagingWasteMapper.humanizeTag("en:can"))
        assertEquals("", PackagingWasteMapper.humanizeTag(null))
        assertEquals("", PackagingWasteMapper.humanizeTag(""))
    }

    @Test
    fun testResolveComponentsWithMaterialAndShape() {
        val product = OffProductDto(
            code = "1234567890",
            productName = "Sparkling Water",
            packagings = listOf(
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:bottle", "Bottle"),
                    material = OffTaxonomyItemDto("en:clear-glass", "Clear glass"),
                    recycling = OffTaxonomyItemDto("en:recycle", "Recycle"),
                    weightMeasured = 150.0
                ),
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:cap", "Cap"),
                    material = OffTaxonomyItemDto("en:hdpe-2", "HDPE"),
                    recycling = OffTaxonomyItemDto("en:recycle", "Recycle"),
                    weight = 2.5
                ),
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:label", "Label"),
                    material = OffTaxonomyItemDto("en:paper", "Paper"),
                    recycling = OffTaxonomyItemDto("en:discard", "Discard"),
                    weightEstimated = 0.5
                )
            )
        )

        val components = PackagingWasteMapper.resolveComponents(product)
        assertEquals(3, components.size)

        // Glass bottle
        val bottle = components[0]
        assertEquals("Bottle", bottle.shape)
        assertEquals("Clear glass", bottle.material)
        assertEquals("Recyclable", bottle.category)
        assertEquals("Glass", bottle.subclass)
        assertEquals("Recycle", bottle.disposalAction)
        assertEquals(150.0, bottle.weightGrams)

        // HDPE cap
        val cap = components[1]
        assertEquals("Cap", cap.shape)
        assertEquals("HDPE", cap.material)
        assertEquals("Recyclable", cap.category)
        assertEquals("Plastic", cap.subclass)
        assertEquals("Recycle", cap.disposalAction)
        assertEquals(2.5, cap.weightGrams)

        // Paper label
        val label = components[2]
        assertEquals("Label", label.shape)
        assertEquals("Paper", label.material)
        assertEquals("Recyclable", label.category)
        assertEquals("Paper", label.subclass)
        assertEquals("Discard", label.disposalAction)
        assertEquals(0.5, label.weightGrams)
    }

    @Test
    fun testWeightPrecedenceMeasuredOverDeclaredOverEstimated() {
        val componentWithAllWeights = OffPackagingComponentDto(
            material = OffTaxonomyItemDto("en:pet-1"),
            weightMeasured = 12.0,
            weight = 15.0,
            weightEstimated = 18.0
        )
        val product1 = OffProductDto(packagings = listOf(componentWithAllWeights))
        assertEquals(12.0, PackagingWasteMapper.resolveComponents(product1).first().weightGrams)

        val componentWithDeclaredAndEstimated = OffPackagingComponentDto(
            material = OffTaxonomyItemDto("en:pet-1"),
            weight = 15.0,
            weightEstimated = 18.0
        )
        val product2 = OffProductDto(packagings = listOf(componentWithDeclaredAndEstimated))
        assertEquals(15.0, PackagingWasteMapper.resolveComponents(product2).first().weightGrams)

        val componentWithEstimatedOnly = OffPackagingComponentDto(
            material = OffTaxonomyItemDto("en:pet-1"),
            weightEstimated = 18.0
        )
        val product3 = OffProductDto(packagings = listOf(componentWithEstimatedOnly))
        assertEquals(18.0, PackagingWasteMapper.resolveComponents(product3).first().weightGrams)
    }

    @Test
    fun testShapeFallbackWhenMaterialNull() {
        val product = OffProductDto(
            packagings = listOf(
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:can"),
                    material = null,
                    weight = 14.0
                )
            )
        )

        val components = PackagingWasteMapper.resolveComponents(product)
        assertEquals(1, components.size)
        val can = components.first()
        assertEquals("Can", can.shape)
        assertEquals("Metal", can.material)
        assertEquals("Recyclable", can.category)
        assertEquals("Metal", can.subclass)
        assertEquals("Recycle", can.disposalAction)
        assertEquals(14.0, can.weightGrams)
    }

    @Test
    fun testResolvePrimaryComponentReturnsHeaviestOrFirst() {
        val emptyProduct = OffProductDto()
        assertNull(PackagingWasteMapper.resolvePrimaryComponent(emptyProduct))

        val productWithDifferentWeights = OffProductDto(
            packagings = listOf(
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:cap"),
                    material = OffTaxonomyItemDto("en:pp-5"),
                    weight = 3.0
                ),
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:bottle"),
                    material = OffTaxonomyItemDto("en:pet-1"),
                    weight = 30.0
                )
            )
        )
        val primary = PackagingWasteMapper.resolvePrimaryComponent(productWithDifferentWeights)
        assertNotNull(primary)
        assertEquals("Bottle", primary.shape)
        assertEquals(30.0, primary.weightGrams)

        // Equal weights: should pick first
        val productWithEqualWeights = OffProductDto(
            packagings = listOf(
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:box"),
                    material = OffTaxonomyItemDto("en:cardboard"),
                    weight = 20.0
                ),
                OffPackagingComponentDto(
                    shape = OffTaxonomyItemDto("en:tray"),
                    material = OffTaxonomyItemDto("en:plastic"),
                    weight = 20.0
                )
            )
        )
        val firstOfEqual = PackagingWasteMapper.resolvePrimaryComponent(productWithEqualWeights)
        assertNotNull(firstOfEqual)
        assertEquals("Box", firstOfEqual.shape)
    }

    @Test
    fun testOffProductResponseJsonDeserialization() {
        val jsonString = """
            {
                "code": "3017620422003",
                "status": "success",
                "product": {
                    "code": "3017620422003",
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "packagings_complete": 1,
                    "packagings": [
                        {
                            "shape": { "id": "en:jar", "lc_name": "Jar" },
                            "material": { "id": "en:clear-glass", "lc_name": "Clear glass" },
                            "recycling": { "id": "en:recycle", "lc_name": "Recycle" },
                            "weight_measured": 220.0
                        },
                        {
                            "shape": { "id": "en:lid", "lc_name": "Lid" },
                            "material": { "id": "en:pp-5", "lc_name": "Polypropylene" },
                            "recycling": { "id": "en:recycle", "lc_name": "Recycle" },
                            "weight_measured": 12.0
                        }
                    ],
                    "packaging_materials_tags": ["en:clear-glass", "en:pp-5"],
                    "packaging_shapes_tags": ["en:jar", "en:lid"],
                    "packaging_recycling_tags": ["en:recycle"],
                    "ecoscore_grade": "e"
                }
            }
        """.trimIndent()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val response = json.decodeFromString<OffProductResponse>(jsonString)
        assertEquals("3017620422003", response.code)
        assertEquals("success", response.status)
        assertNotNull(response.product)
        assertEquals("Nutella", response.product?.productName)
        assertEquals(2, response.product?.packagings?.size)

        val primary = PackagingWasteMapper.resolvePrimaryComponent(response.product!!)
        assertNotNull(primary)
        assertEquals("Jar", primary.shape)
        assertEquals("Clear glass", primary.material)
        assertEquals("Recyclable", primary.category)
        assertEquals("Glass", primary.subclass)
        assertEquals("Recycle", primary.disposalAction)
        assertEquals(220.0, primary.weightGrams)
    }
}
