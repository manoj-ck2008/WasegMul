package com.agrelius.wasegmul.knowledge

object WasteKnowledgeBase {

    /**
     * Fixed scientifically calculated weights (kg) for consistency.
     */
    private val MATERIAL_WEIGHTS = mapOf(
        "Plastic" to 0.045,
        "Glass" to 0.250,
        "Metal" to 0.015,
        "Paper" to 0.010,
        "Cardboard" to 0.150,
        "Battery" to 0.025,
        "Mobile" to 0.180,
        "Laptop" to 1.800,
        "Organic" to 0.120,
        "Miscellaneous Trash" to 0.030,
        "Air-Conditioner" to 45.0,
        "Washing Machine" to 65.0,
        "Television" to 12.0,
        "Refrigerator" to 75.0,
        "Microwave" to 15.0,
        "Printer" to 5.0,
        "Keyboard" to 0.600,
        "Mouse" to 0.100
    )

    fun getEstimatedWeight(subclass: String): Double {
        // Normalizing the subclass string to match the map
        val normalized = subclass.trim()
        return MATERIAL_WEIGHTS[normalized] ?: 0.050
    }

    fun getInfo(category: String, subclass: String): WasteInfo {
        return when (category.lowercase()) {
            "recyclable" -> getRecyclableInfo(subclass)
            "organic" -> getOrganicInfo()
            "e-waste" -> getEWasteInfo()
            "trash" -> getTrashInfo()
            else -> getGeneralInfo()
        }
    }

    private fun getRecyclableInfo(subclass: String) = WasteInfo(
        disposalGuide = "• RINSE: Remove liquids/food to prevent contamination.\n• SORT: Separate caps (HDPE) from bottles (PET).\n• COMPACT: Crush to reduce carbon footprint during logistics.",
        environmentalImpact = "Recycling $subclass diverts landfill mass and reduces energy consumption by up to 75% compared to raw extraction.",
        recyclingBenefits = "Recycled $subclass saves significant water resources and prevents leachate into groundwater ecosystems.",
        sources = "EPA Municipal Waste Data (2024), ISWA Global Research"
    )

    private fun getOrganicInfo() = WasteInfo(
        disposalGuide = "• COMPOST: Use a binary bin system.\n• DRY: Avoid excessive moisture to prevent odor.\n• EXCLUDE: No plastic stickers or treated paper.",
        environmentalImpact = "Organic waste in landfills is a primary methane source. Diversion reduces Greenhouse Gas emissions by 60%.",
        recyclingBenefits = "Converts to nitrogen-rich soil amendment, sequestering carbon directly in the Earth.",
        sources = "FAO Sustainability Reports, IPCC Climate Guides"
    )

    private fun getEWasteInfo() = WasteInfo(
        disposalGuide = "• SAFETY: Do not puncture batteries.\n• DATA: Wipe internal memory.\n• DROPOFF: Authorized E-Steward certified centers only.",
        environmentalImpact = "Prevents toxic lead, mercury, and flame retardants from entering the food chain via soil absorption.",
        recyclingBenefits = "Recovers rare earth elements (Lithium, Cobalt) essential for renewable energy transition.",
        sources = "UN Global E-waste Monitor, Basel Action Network"
    )

    private fun getTrashInfo() = WasteInfo(
        disposalGuide = "• SEAL: Bag securely to prevent microplastic shedding.\n• HAZARDS: Ensure no lithium-ion batteries are present.\n• REDUCE: Consider reusable alternatives for next time.",
        environmentalImpact = "Current landfill degradation time: 500+ years. Primary source of ocean plastic pollution.",
        recyclingBenefits = "While not currently recyclable, contained disposal prevents immediate ecosystem collapse.",
        sources = "National Geographic Ocean Impact, UNEP Waste Report"
    )

    private fun getGeneralInfo() = WasteInfo(
        disposalGuide = "Consult your regional waste authority for material-specific protocols.",
        environmentalImpact = "Correct identification is the first step in the Circular Economy.",
        recyclingBenefits = "Individual action scales to global environmental resilience.",
        sources = "agrelius Sustainability Research"
    )
}

data class WasteInfo(
    val disposalGuide: String,
    val environmentalImpact: String,
    val recyclingBenefits: String,
    val sources: String
)
