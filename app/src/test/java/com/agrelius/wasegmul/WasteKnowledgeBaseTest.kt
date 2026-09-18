package com.agrelius.wasegmul

import org.junit.Assert.*
import org.junit.Test

class WasteKnowledgeBaseTest {

    private val all30Subclasses = listOf(
        "Air-Conditioner",
        "Battery",
        "Cardboard",
        "Electronic Component",
        "Electronic Device",
        "Glass",
        "Keyboard",
        "Laptop",
        "Metal",
        "Microwave",
        "Miscellaneous Trash",
        "Mobile",
        "Mouse",
        "Organic",
        "PCB",
        "Paper",
        "Plastic",
        "Player",
        "Printer",
        "Refrigerator",
        "Television",
        "Textile Trash",
        "Washing Machine",
        "automobile wastes",
        "clothing",
        "disposable_plastic_cutlery",
        "light bulbs",
        "shoes",
        "styrofoam_cups",
        "styrofoam_food_containers"
    )

    @Test
    fun testAll30Subclasses_returnCompleteKnowledge() {
        all30Subclasses.forEach { subclass ->
            val category = WasteMapping.getCategory(subclass)
            val info = WasteKnowledgeBase.getInfo(category, subclass)

            assertTrue("Disposal guide for '$subclass' should not be blank", info.disposalGuide.isNotBlank())
            assertTrue("Environmental impact for '$subclass' should not be blank", info.environmentalImpact.isNotBlank())
            assertTrue("Recycling benefits for '$subclass' should not be blank", info.recyclingBenefits.isNotBlank())
            assertTrue("Sources for '$subclass' should not be blank", info.sources.isNotBlank())
        }
    }

    @Test
    fun testUncertainAndUnknownCategories_returnSafeAdvice() {
        val uncertain = WasteKnowledgeBase.getInfo("Uncertain", "Plastic")
        assertTrue(uncertain.disposalGuide.contains("verify manually", ignoreCase = true))

        val unknown = WasteKnowledgeBase.getInfo("Unknown", "Paper")
        assertTrue(unknown.disposalGuide.contains("verify manually", ignoreCase = true))
    }

    @Test
    fun testCategoryLevelFallback_whenSubclassUncertain() {
        val recyclableInfo = WasteKnowledgeBase.getInfo("Recyclable", "Uncertain")
        assertTrue("Category-level fallback should be used", recyclableInfo.disposalGuide.contains("CATEGORY-LEVEL"))
        assertTrue("Should include recycling instructions", recyclableInfo.disposalGuide.contains("recycling bin", ignoreCase = true))

        val ewasteInfo = WasteKnowledgeBase.getInfo("E-Waste", "Unknown")
        assertTrue("Category-level fallback should be used for ewaste", ewasteInfo.disposalGuide.contains("CATEGORY-LEVEL"))

        val organicInfo = WasteKnowledgeBase.getInfo("Organic", "Uncertain")
        assertTrue("Category-level fallback should be used for organic", organicInfo.disposalGuide.contains("CATEGORY-LEVEL"))

        val trashInfo = WasteKnowledgeBase.getInfo("Trash", "Unknown")
        assertTrue("Category-level fallback should be used for trash", trashInfo.disposalGuide.contains("CATEGORY-LEVEL"))
    }

    @Test
    fun testHazardousAndResidual_haveCategoryGuidance() {
        val hz = WasteKnowledgeBase.getInfo("Hazardous", "Uncertain")
        assertTrue(hz.disposalGuide.contains("CATEGORY-LEVEL"))
        val residual = WasteKnowledgeBase.getInfo("Residual", "Unknown")
        assertTrue(residual.disposalGuide.contains("CATEGORY-LEVEL"))
    }

    @Test
    fun testCapitalisedExtendedLabel_hitsSpecificBranch() {
        val info = WasteKnowledgeBase.getInfo("Recyclable", "Bottle")
        assertTrue("Should be specific Plastic guidance", info.disposalGuide.contains("RINSE"))
    }
}
