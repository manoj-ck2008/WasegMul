package com.agrelius.wasegmul.data

/**
 * Definitive mapping of subclasses to their parent categories and standardized weights.
 * This acts as the source of truth for the MLArbitrator and KnowledgeBase.
 */
object WasteMapping {
    data class MaterialMetaData(
        val category: String,
        val weightKg: Double,
        val isHazardous: Boolean = false
    )

    val MAPPING = mapOf(
        "Air-Conditioner" to MaterialMetaData("E-Waste", 45.0),
        "Battery" to MaterialMetaData("E-Waste", 0.025, isHazardous = true),
        "Cardboard" to MaterialMetaData("Recyclable", 0.150),
        "Electronic Component" to MaterialMetaData("E-Waste", 0.050),
        "Electronic Device" to MaterialMetaData("E-Waste", 0.500),
        "Glass" to MaterialMetaData("Recyclable", 0.250),
        "Keyboard" to MaterialMetaData("E-Waste", 0.600),
        "Laptop" to MaterialMetaData("E-Waste", 1.800),
        "Metal" to MaterialMetaData("Recyclable", 0.015),
        "Microwave" to MaterialMetaData("E-Waste", 15.0),
        "Miscellaneous Trash" to MaterialMetaData("Trash", 0.030),
        "Mobile" to MaterialMetaData("E-Waste", 0.180),
        "Mouse" to MaterialMetaData("E-Waste", 0.100),
        "Organic" to MaterialMetaData("Organic", 0.120),
        "PCB" to MaterialMetaData("E-Waste", 0.080),
        "Paper" to MaterialMetaData("Recyclable", 0.010),
        "Plastic" to MaterialMetaData("Recyclable", 0.045),
        "Player" to MaterialMetaData("E-Waste", 0.300),
        "Printer" to MaterialMetaData("E-Waste", 5.0),
        "Refrigerator" to MaterialMetaData("E-Waste", 75.0),
        "Television" to MaterialMetaData("E-Waste", 12.0),
        "Textile Trash" to MaterialMetaData("Trash", 0.100),
        "Washing Machine" to MaterialMetaData("E-Waste", 65.0),
        "automobile wastes" to MaterialMetaData("Trash", 5.0, isHazardous = true),
        "clothing" to MaterialMetaData("Recyclable", 0.300), // Assuming donation/textile recycling
        "disposable_plastic_cutlery" to MaterialMetaData("Trash", 0.010),
        "light bulbs" to MaterialMetaData("Trash", 0.050, isHazardous = true), // Often not curbside recyclable
        "shoes" to MaterialMetaData("Recyclable", 0.500),
        "styrofoam_cups" to MaterialMetaData("Trash", 0.005),
        "styrofoam_food_containers" to MaterialMetaData("Trash", 0.010)
    )

    fun getCategory(subclass: String): String {
        return MAPPING[subclass]?.category ?: "Unknown"
    }

    fun getWeight(subclass: String): Double {
        return MAPPING[subclass]?.weightKg ?: 0.05
    }
}
