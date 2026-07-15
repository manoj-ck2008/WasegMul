package com.agrelius.wasegmul

object WasteKnowledgeBase {

    fun getInfo(category: String, subclass: String): WasteInfo {
        if (category == "Uncertain") {
            return getGeneralInfo("Low confidence detection. Please verify manually.")
        }

        return when (subclass.trim()) {
            "Battery" -> WasteInfo(
                disposalGuide = "• HAZARD: Fire risk. Do NOT put in regular trash or recycling bins.\n• TAPE: Cover terminals with clear tape to prevent short circuits.\n• SPECIALIST: Drop off at dedicated battery recycling kiosks or e-waste centers.",
                environmentalImpact = "Prevents toxic chemicals (Cadmium, Lead, Lithium) from leaking into soil and water tables.",
                recyclingBenefits = "Rare metals like Cobalt and Lithium are recovered for new battery production.",
                sources = "EPA Hazardous Waste Guidelines"
            )
            "Plastic" -> WasteInfo(
                disposalGuide = "• RINSE: Remove food residue.\n• CHECK: Ensure it's Resin Code #1, #2, or #5 (most common recyclables).\n• BINS: Place in yellow/blue recycling bins.",
                environmentalImpact = "Diverts material from 450-year decomposition cycles in landfills.",
                recyclingBenefits = "Reduces petroleum demand for virgin plastic production.",
                sources = "Sustainable Packaging Coalition"
            )
            "Paper", "Cardboard" -> WasteInfo(
                disposalGuide = "• DRY: Wet paper/cardboard cannot be recycled.\n• FLATTEN: Save space in transport to reduce carbon emissions.\n• REMOVE: Strip off excessive tape or plastic wrapping.",
                environmentalImpact = "Saves trees and reduces methane produced by organic decomposition in landfills.",
                recyclingBenefits = "Uses 40% less energy than manufacturing from virgin wood pulp.",
                sources = "American Forest & Paper Association"
            )
            "Electronic Device", "Mobile", "Laptop", "Keyboard", "Mouse", "Printer" -> WasteInfo(
                disposalGuide = "• DATA: Wipe all personal storage before disposal.\n• CABLES: Keep power cords with the device if possible.\n• RECOVERY: Take to an R2 or e-Stewards certified recycler.",
                environmentalImpact = "Electronics contain heavy metals that contaminate groundwater if landfilled.",
                recyclingBenefits = "Recovers gold, silver, and copper, reducing the need for destructive mining.",
                sources = "UN Global E-waste Monitor"
            )
            "Organic" -> WasteInfo(
                disposalGuide = "• COMPOST: Best for garden soil enhancement.\n• NO PLASTIC: Ensure no stickers or plastic liners are included.\n• BINS: Use dedicated brown/green organic bins.",
                environmentalImpact = "Reduces landfill methane (a potent greenhouse gas).",
                recyclingBenefits = "Returns nutrients to the soil, supporting local biodiversity.",
                sources = "Composting Council"
            )
            "Metal" -> WasteInfo(
                disposalGuide = "• CLEAN: Rinse out food or paint residue.\n• TYPES: Aluminum and Steel are infinitely recyclable.\n• SORT: Do not mix with plastic if using single-stream.",
                environmentalImpact = "Mining ore is 95% more energy-intensive than recycling existing metal.",
                recyclingBenefits = "Metals maintain structural integrity throughout multiple recycling loops.",
                sources = "International Aluminum Institute"
            )
            "Glass" -> WasteInfo(
                disposalGuide = "• RINSE: Wash away sugars or oils.\n• SORT: Separate by color if required by local municipality.\n• NO CERAMICS: Pyrex or ceramics contaminate glass recycling melts.",
                environmentalImpact = "Glass takes 1 million years to decompose; recycling is the only sustainable path.",
                recyclingBenefits = "Cullet (crushed glass) reduces furnace temperatures and CO2 emissions.",
                sources = "Glass Packaging Institute"
            )
            "light bulbs" -> WasteInfo(
                disposalGuide = "• FRAGILE: Wrap in paper to prevent injury if broken.\n• MERCURY: CFLs contain mercury; must go to hazardous waste drop-off.\n• LED/INCANDESCENT: Check local rules; often treated as special trash.",
                environmentalImpact = "Prevents mercury vapor release into the atmosphere.",
                recyclingBenefits = "Glass and metal components can be recovered safely.",
                sources = "Energy Star Disposal Guide"
            )
            "styrofoam_cups", "styrofoam_food_containers" -> WasteInfo(
                disposalGuide = "• TRASH: Usually NOT recyclable in curbside bins.\n• CLEAN: If your city accepts EPS, it must be spotless.\n• REDUCE: Switch to reusable containers.",
                environmentalImpact = "Extremely persistent in ocean eco systems; breaks into microplastics.",
                recyclingBenefits = "While technically possible, low density makes transport energy-inefficient.",
                sources = "Ocean Conservancy"
            )
            else -> getGeneralInfo("Follow local municipal guidelines for $subclass.")
        }
    }

    private fun getGeneralInfo(customGuide: String) = WasteInfo(
        disposalGuide = customGuide,
        environmentalImpact = "Correct identification is the first step in the Circular Economy.",
        recyclingBenefits = "Individual action scales to global environmental resilience.",
        sources = "agrelius Sustainability Research"
    )
}
