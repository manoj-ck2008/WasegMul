package com.agrelius.wasegmul

object DecayTimeData {

    data class DecayTimeInfo(
        val subclass: String,
        val minYears: Double,
        val maxYears: Double,
        val displayText: String,
        val impactWarning: String,
        val severityLevel: Int
    )

    private val subclassLookup = mapOf(
        "Air-Conditioner" to DecayTimeInfo(
            "Air-Conditioner", 100.0, 1000.0, "100 to 1,000+ years",
            "Improper disposal releases CFCs and HFCs that directly deplete the ozone layer. Heavy metals and compressor oils can severely contaminate local soil and groundwater over centuries.", 3
        ),
        "Battery" to DecayTimeInfo(
            "Battery", 100.0, 100.0, "100+ years",
            "Batteries continuously leach toxic heavy metals like lead, cadmium, and mercury into the soil. These toxins eventually reach the groundwater, causing severe and long-lasting ecological damage.", 3
        ),
        "Cardboard" to DecayTimeInfo(
            "Cardboard", 0.16, 0.16, "2 months",
            "While cardboard decomposes relatively quickly, placing it in an anaerobic landfill environment generates methane gas. Methane is a potent greenhouse gas that heavily contributes to climate change.", 1
        ),
        "Electronic Component" to DecayTimeInfo(
            "Electronic Component", 100.0, 1000.0, "100 to 1,000+ years",
            "Electronic components contain hazardous lead solder and rare earth elements that persist indefinitely. Their degradation fragments release micro-toxins that pollute both terrestrial and aquatic ecosystems.", 2
        ),
        "Electronic Device" to DecayTimeInfo(
            "Electronic Device", 100.0, 1000.0, "100 to 1,000+ years",
            "Circuit boards and plastics within these devices take centuries to break down. During this time, they slowly leak highly toxic heavy metals into the surrounding environment.", 3
        ),
        "Glass" to DecayTimeInfo(
            "Glass", 1000000.0, 1000000.0, "1 million+ years",
            "Glass is highly inert and poses little chemical threat, but it consumes physical space almost permanently. A glass bottle dropped today will likely outlast all current human structures.", 1
        ),
        "Keyboard" to DecayTimeInfo(
            "Keyboard", 100.0, 1000.0, "100 to 1,000+ years",
            "The hard plastic housing and internal electronic matrix resist natural breakdown entirely. Over decades, they fragment into microplastics while the internal wiring leaches metals.", 2
        ),
        "Laptop" to DecayTimeInfo(
            "Laptop", 100.0, 1000.0, "100 to 1,000+ years",
            "Laptops combine the worst environmental hazards: lithium batteries, heavy metals, and durable plastics. A single discarded unit can contaminate massive volumes of soil and water as it degrades.", 3
        ),
        "Metal" to DecayTimeInfo(
            "Metal", 100.0, 500.0, "100 to 500 years",
            "Metals gradually undergo oxidation, creating rust runoff that alters local soil chemistry. Certain alloys also contain toxic trace metals that pose a threat to plant and animal life.", 2
        ),
        "Microwave" to DecayTimeInfo(
            "Microwave", 100.0, 1000.0, "100 to 1,000+ years",
            "Microwaves contain complex magnetrons, hazardous capacitors, and thick plastic casings. Discarded units introduce highly toxic electronic waste and persistent synthetic materials into landfills.", 3
        ),
        "Miscellaneous Trash" to DecayTimeInfo(
            "Miscellaneous Trash", 10.0, 100.0, "10 to 100 years",
            "Mixed trash breaks down at highly variable rates depending on composition. Synthetic materials within the mix fragment into micro-pollutants that disrupt local ecosystems.", 2
        ),
        "Mobile" to DecayTimeInfo(
            "Mobile", 100.0, 1000.0, "100 to 1,000+ years",
            "Mobile phones pack dense lithium batteries and rare earth elements into a small frame. Once corroded, they act as concentrated sources of severe heavy metal contamination.", 3
        ),
        "Mouse" to DecayTimeInfo(
            "Mouse", 100.0, 1000.0, "100 to 1,000+ years",
            "Computer mice are composed primarily of durable plastics and small electronics. They inevitably turn into persistent microplastics while trace metals seep into the ground.", 2
        ),
        "Organic" to DecayTimeInfo(
            "Organic", 0.02, 0.16, "1 week to 2 months",
            "Organic matter decomposes rapidly, but in tightly packed landfills it undergoes anaerobic digestion. This process produces high volumes of methane, accelerating global warming.", 0
        ),
        "PCB" to DecayTimeInfo(
            "PCB", 100.0, 1000.0, "100 to 1,000+ years",
            "Printed circuit boards contain toxic heavy metals and highly persistent brominated flame retardants. These chemicals do not break down naturally and accumulate dangerously in food chains.", 3
        ),
        "Paper" to DecayTimeInfo(
            "Paper", 0.04, 0.11, "2 to 6 weeks",
            "While paper breaks down fast, it often carries chemical bleach residues and synthetic inks. These chemicals can seep into soil during the decomposition process.", 0
        ),
        "Plastic" to DecayTimeInfo(
            "Plastic", 100.0, 1000.0, "100 to 1,000+ years",
            "Plastics never truly biodegrade; they only fragment into dangerous microplastics. These particles devastate marine ecosystems, enter food chains, and persist for centuries.", 3
        ),
        "Player" to DecayTimeInfo(
            "Player", 100.0, 1000.0, "100 to 1,000+ years",
            "Media players consist of mixed electronics and hard plastic shells. They represent a long-term pollution source that releases both chemical toxins and structural microplastics.", 2
        ),
        "Printer" to DecayTimeInfo(
            "Printer", 100.0, 1000.0, "100 to 1,000+ years",
            "Printers leak highly toxic toner chemicals as their massive plastic housings slowly degrade. These industrial pollutants severely disrupt nearby soil and water health.", 3
        ),
        "Refrigerator" to DecayTimeInfo(
            "Refrigerator", 100.0, 1000.0, "100 to 1,000+ years",
            "Abandoned refrigerators leak potent greenhouse gases like CFCs and HFCs. In addition, their compressor oils and heavy metals heavily pollute the surrounding ground.", 3
        ),
        "Television" to DecayTimeInfo(
            "Television", 100.0, 1000.0, "100 to 1,000+ years",
            "Older TVs contain vast amounts of lead in CRT glass, while newer ones use mercury in their backlights. Both types pose a critical and immediate toxic threat to groundwater.", 3
        ),
        "Textile Trash" to DecayTimeInfo(
            "Textile Trash", 0.5, 100.0, "6 months to 100+ years",
            "Modern textiles are heavily reliant on synthetic fibers like polyester. As they degrade, they shed immense quantities of microplastics that spread through water systems.", 2
        ),
        "Washing Machine" to DecayTimeInfo(
            "Washing Machine", 100.0, 1000.0, "100 to 1,000+ years",
            "Large appliances like washing machines leach motor oils and heavy metals into the earth. Their bulk takes centuries to rust away, leaving behind a permanent toxic footprint.", 3
        ),
        "automobile wastes" to DecayTimeInfo(
            "automobile wastes", 200.0, 2000.0, "200 to 2,000+ years",
            "Automotive waste introduces complex chemical hazards like motor oil, brake fluid, and synthetic rubber. These highly toxic substances decimate local soil fertility and poison water sources.", 3
        ),
        "clothing" to DecayTimeInfo(
            "clothing", 0.5, 100.0, "6 months to 100+ years",
            "Clothing waste not only sheds synthetic microfibers but also leaches chemical dyes. These toxic colorants persist in the environment, polluting waterways for decades.", 2
        ),
        "disposable_plastic_cutlery" to DecayTimeInfo(
            "disposable_plastic_cutlery", 100.0, 500.0, "100 to 500 years",
            "Usually made of polystyrene, this cutlery resists biological degradation almost completely. It slowly shatters into jagged microplastics that are frequently ingested by wildlife.", 3
        ),
        "light bulbs" to DecayTimeInfo(
            "light bulbs", 100.0, 1000.0, "100 to 1,000+ years",
            "Many light bulbs contain highly dangerous mercury vapor and toxic lead solder. When broken in a landfill, these neurotoxins immediately vaporize or wash into the soil.", 3
        ),
        "shoes" to DecayTimeInfo(
            "shoes", 25.0, 50.0, "25 to 50+ years",
            "Modern shoes are complex assemblies of rubber soles, synthetic uppers, and chemical glues. They resist decay while slowly releasing a cocktail of synthetic compounds.", 2
        ),
        "styrofoam_cups" to DecayTimeInfo(
            "styrofoam_cups", 50.0, 50.0, "50+ years",
            "Polystyrene foam is an ecological disaster that never fully biodegrades. It continuously fragments, spreading lightweight toxic particles across vast distances via wind and water.", 3
        ),
        "styrofoam_food_containers" to DecayTimeInfo(
            "styrofoam_food_containers", 50.0, 50.0, "50+ years",
            "These containers are practically unrecyclable due to food contamination. They endure in landfills for centuries, releasing styrene chemicals and persistent microplastics.", 3
        )
    )

    private val categoryFallbacks = mapOf(
        "E-Waste" to DecayTimeInfo(
            "Generic E-Waste", 100.0, 1000.0, "100 to 1,000+ years",
            "Electronic waste contains heavy metals and toxic chemicals that endure for centuries. These components severely contaminate soil and groundwater if not properly recycled.", 3
        ),
        "Organic" to DecayTimeInfo(
            "Generic Organic", 0.05, 0.5, "A few weeks to months",
            "Organic materials break down quickly but produce methane gas in tightly packed landfills. Composting is necessary to prevent this harmful greenhouse gas emission.", 0
        ),
        "Recyclable" to DecayTimeInfo(
            "Generic Recyclable", 50.0, 500.0, "Decades to centuries",
            "Recyclable materials like basic plastics and metals degrade very slowly. Without proper recycling, they contribute significantly to long-term environmental pollution.", 2
        ),
        "Trash" to DecayTimeInfo(
            "Generic Trash", 50.0, 1000.0, "Decades to centuries",
            "General mixed trash contains persistent synthetic materials that resist natural breakdown. They steadily leak micro-pollutants into the local ecosystem.", 2
        )
    )

    fun getDecayInfo(subclass: String, category: String): DecayTimeInfo {
        return subclassLookup[subclass] 
            ?: subclassLookup.entries.find { it.key.equals(subclass, ignoreCase = true) }?.value
            ?: categoryFallbacks[category]
            ?: categoryFallbacks.entries.find { it.key.equals(category, ignoreCase = true) }?.value
            ?: DecayTimeInfo(
                "Unknown Waste", 50.0, 500.0, "Decades to centuries",
                "Unidentified waste materials often contain synthetic compounds that do not break down easily. They pose a lasting, unknown threat to natural habitats.", 2
            )
    }

    fun getComparison(minYears: Double): String {
        return when {
            minYears < 1.0 -> "Less than a single season"
            minYears < 50.0 -> "Longer than a generation"
            minYears < 100.0 -> "Your grandchildren would still see it"
            minYears < 500.0 -> "Outlasts every building standing today"
            minYears < 1000.0 -> "Longer than most civilizations have existed"
            minYears < 1000000.0 -> "Longer than recorded human history"
            else -> "Effectively permanent - it will outlast humanity itself"
        }
    }
}
