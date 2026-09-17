package com.agrelius.wasegmul

/**
 * Static mapping from each subclass label (as emitted by the TFLite subclass model) to
 * its canonical category, an estimated per-unit weight (kg), and whether it is hazardous.
 *
 * Weights are conservative single-unit estimates used only for the aggregate "impact"
 * dashboard; they are not intended to be precise for billing or compliance.
 */
object WasteMapping {

    /** Category returned when a subclass label has no mapping entry. */
    const val UNKNOWN = "Unknown"

    /** Category used when confidence is too low to trust either model. */
    const val UNCERTAIN = "Uncertain"

    data class MaterialMetaData(
        val category: String,
        val weightKg: Double,
        val isHazardous: Boolean = false
    )

    val MAPPING = mapOf(
        "Air-Conditioner" to MaterialMetaData("E-Waste", 45.0, isHazardous = true),
        "Battery" to MaterialMetaData("E-Waste", 0.025, isHazardous = true),
        "Cardboard" to MaterialMetaData("Recyclable", 0.150),
        "Electronic Component" to MaterialMetaData("E-Waste", 0.050, isHazardous = true),
        "Electronic Device" to MaterialMetaData("E-Waste", 0.500),
        "Glass" to MaterialMetaData("Recyclable", 0.250),
        "Keyboard" to MaterialMetaData("E-Waste", 0.600),
        "Laptop" to MaterialMetaData("E-Waste", 1.800),
        "Metal" to MaterialMetaData("Recyclable", 0.150),
        "Microwave" to MaterialMetaData("E-Waste", 15.0, isHazardous = true),
        "Miscellaneous Trash" to MaterialMetaData("Trash", 0.030),
        "Mobile" to MaterialMetaData("E-Waste", 0.180),
        "Mouse" to MaterialMetaData("E-Waste", 0.100),
        "Organic" to MaterialMetaData("Organic", 0.120),
        "PCB" to MaterialMetaData("E-Waste", 0.080, isHazardous = true),
        "Paper" to MaterialMetaData("Recyclable", 0.010),
        "Plastic" to MaterialMetaData("Recyclable", 0.045),
        "Player" to MaterialMetaData("E-Waste", 0.300),
        "Printer" to MaterialMetaData("E-Waste", 5.0, isHazardous = true),
        "Refrigerator" to MaterialMetaData("E-Waste", 75.0, isHazardous = true),
        "Television" to MaterialMetaData("E-Waste", 12.0, isHazardous = true),
        "Textile Trash" to MaterialMetaData("Trash", 0.100),
        "Washing Machine" to MaterialMetaData("E-Waste", 65.0),
        "automobile wastes" to MaterialMetaData("Trash", 5.0, isHazardous = true),
        "clothing" to MaterialMetaData("Recyclable", 0.300),
        "disposable_plastic_cutlery" to MaterialMetaData("Trash", 0.010),
        "light bulbs" to MaterialMetaData("Trash", 0.050, isHazardous = true),
        "shoes" to MaterialMetaData("Recyclable", 0.500),
        "styrofoam_cups" to MaterialMetaData("Trash", 0.005),
        "styrofoam_food_containers" to MaterialMetaData("Trash", 0.010)
    )

    /**
     * Extended mappings for YOLO object detector classes and common material synonyms.
     */
    val EXTENDED_MAPPING = mapOf(
        "bottle" to MaterialMetaData("Recyclable", 0.040),
        "can" to MaterialMetaData("Recyclable", 0.015),
        "cigarette" to MaterialMetaData("Trash", 0.001, isHazardous = true),
        "cup" to MaterialMetaData("Trash", 0.010),
        "electronic" to MaterialMetaData("E-Waste", 0.500),
        "food_waste" to MaterialMetaData("Organic", 0.150),
        "glass_container" to MaterialMetaData("Recyclable", 0.250),
        "plastic_bag" to MaterialMetaData("Recyclable", 0.005),
        "plastic_container" to MaterialMetaData("Recyclable", 0.030),
        "plastic_wrapper" to MaterialMetaData("Trash", 0.002),
        "textile" to MaterialMetaData("Trash", 0.100),
        "cell phone" to MaterialMetaData("E-Waste", 0.180),
        "wine glass" to MaterialMetaData("Recyclable", 0.200),
        "book" to MaterialMetaData("Recyclable", 0.300),
        "banana" to MaterialMetaData("Organic", 0.120),
        "apple" to MaterialMetaData("Organic", 0.120)
    )

    private val CASE_INSENSITIVE_MAPPING: Map<String, MaterialMetaData> =
        (MAPPING + EXTENDED_MAPPING).entries.associate { (k, v) -> k.lowercase() to v }

    /** Returns the canonical category for [subclass], or [UNKNOWN] if it is not mapped. */
    fun getCategory(subclass: String): String =
        CASE_INSENSITIVE_MAPPING[subclass.trim().lowercase()]?.category ?: UNKNOWN

    /** Returns the estimated weight (kg) for [subclass], defaulting to 0.05 kg when unmapped. */
    fun getWeight(subclass: String): Double =
        CASE_INSENSITIVE_MAPPING[subclass.trim().lowercase()]?.weightKg ?: 0.05

    /** True when the subclass is marked hazardous (batteries, bulbs, auto waste, ...). */
    fun isHazardous(subclass: String): Boolean =
        CASE_INSENSITIVE_MAPPING[subclass.trim().lowercase()]?.isHazardous ?: false
}
