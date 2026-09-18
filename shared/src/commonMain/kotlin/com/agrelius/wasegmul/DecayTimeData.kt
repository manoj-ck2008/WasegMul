package com.agrelius.wasegmul

/**
 * Decomposition timelines per waste subclass.
 *
 * ## Provenance (estimates, not lab measurements)
 * Ranges below are literature-based planning estimates compiled from widely-cited public
 * sources — US EPA Advancing Sustainable Materials Management, UNEP single-use-plastics
 * reporting, Ocean Conservancy marine-debris figures, International Aluminium Institute
 * (aluminium persistence/recycling), Glass Packaging Institute (container glass), Truth
 * Initiative / Keep America Beautiful (cigarette butts ~10–12 years). Landfill conditions
 * (anaerobic, dark, compressed) slow most decay versus open environments, so ranges skew
 * conservative. Present [DecayTimeInfo.displayText] verbatim; never interpolate raw year
 * counts into "0 YEARS"-style animations for sub-year items (use displayText throughout).
 *
 * ## Severity scale (persistence × toxicity, 0–3)
 * - 0 = benign and short-lived (weeks–months; landfill methane only).
 * - 1 = long-lived but chemically inert bulk (glass, plain metal mass).
 * - 2 = persistent AND shedding micro-pollutants / dyes / moderately toxic leachate.
 * - 3 = acutely toxic (heavy metals, refrigerants, toner, nicotine) and/or effectively
 *   permanent synthetics. Glass is deliberately sev-1 despite 1M-year persistence: it is
 *   inert bulk, not poison — persistence and toxicity are separate axes and the scale
 *   records their product, not either alone.
 */
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
            "Battery", 100.0, 1000.0, "100 to 1,000+ years (metals persist indefinitely)",
            "Batteries continuously leach toxic heavy metals like lead, cadmium, and mercury into the soil. The metal casings and electrodes persist effectively indefinitely. These toxins eventually reach the groundwater, causing severe and long-lasting ecological damage.", 3
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
            "Structural and mixed metals gradually undergo oxidation, creating rust runoff that alters local soil chemistry. Certain alloys also contain toxic trace metals that pose a threat to plant and animal life. Thin cans decay faster — see the dedicated 'can' entry (80 to 200 years).", 2
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
            "Rigid plastics never truly biodegrade; they only fragment into dangerous microplastics. These particles devastate marine ecosystems, enter food chains, and persist for centuries. Thin film is faster but still hazardous — see 'plastic_bag' (10 to 20 years) and 'plastic_wrapper'.", 3
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
            "automobile wastes", 50.0, 80.0, "50 to 80+ years",
            "The typical unit is tyre-equivalent rubber/steel waste (see WasteMapping): tyres photodegrade over many decades while leaching zinc, oils and micro-rubber. Whole-vehicle hulks persist far longer, but fluids, batteries and tyres follow dedicated hazardous streams — never landfill them mixed.", 3
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
            "styrofoam_cups", 500.0, 1000.0, "500+ years",
            "Polystyrene foam is an ecological disaster that never fully biodegrades. It continuously fragments, spreading lightweight toxic particles across vast distances via wind and water.", 3
        ),
        "styrofoam_food_containers" to DecayTimeInfo(
            "styrofoam_food_containers", 500.0, 1000.0, "500+ years",
            "These containers are practically unrecyclable due to food contamination. They endure in landfills for centuries, releasing styrene chemicals and persistent microplastics.", 3
        ),
        // ── YOLO / EXTENDED shapes (previously fell back to vague category buckets) ──
        "cigarette" to DecayTimeInfo(
            "cigarette", 10.0, 12.0, "10 to 12 years",
            "Cellulose-acetate filters do NOT biodegrade on cigarette timescales: they fragment into microplastics while leaching nicotine, arsenic and heavy metals into stormwater. Among the most-littered items worldwide.", 2
        ),
        "bottle" to DecayTimeInfo(
            "bottle", 450.0, 450.0, "Around 450 years",
            "A PET drink bottle persists on the order of four centuries in landfill or marine conditions (UNEP/Ocean Conservancy figures). Glass bottles are effectively permanent — see 'Glass'.", 3
        ),
        "can" to DecayTimeInfo(
            "can", 80.0, 200.0, "80 to 200 years",
            "An aluminium/steel can oxidises far faster than structural metal but still spans human lifetimes in landfill; recycled in weeks when actually recycled.", 1
        ),
        "plastic_bag" to DecayTimeInfo(
            "plastic_bag", 10.0, 20.0, "10 to 20 years",
            "Thin film photodegrades faster than rigid plastic but shreds into wind-borne microplastics and is a leading marine-entanglement hazard. Never curbside-recycled — store drop-off only.", 2
        ),
        "plastic_wrapper" to DecayTimeInfo(
            "plastic_wrapper", 10.0, 30.0, "10 to 30 years",
            "Multi-layer film fragments like bags and carries food contamination that blocks recycling; treat as persistent litter with the same marine hazard profile as plastic bags.", 2
        ),
        "plastic_container" to DecayTimeInfo(
            "plastic_container", 100.0, 500.0, "100 to 500 years",
            "Rigid food and product containers fragment into microplastics over centuries; tubs and pots share the 'Plastic' persistence profile at smaller unit mass.", 3
        ),
        "cup" to DecayTimeInfo(
            "cup", 20.0, 30.0, "20 to 30 years",
            "A 'paper' cup is paper bonded to a polyethylene moisture barrier: the paper rots, the plastic liner persists for decades. Sleeves and lids follow their own streams.", 1
        ),
        "textile" to DecayTimeInfo(
            "textile", 0.5, 100.0, "6 months to 100+ years",
            "Natural fibres rot within months; polyester-blend scraps shed microfibres for a century. Same profile as 'clothing' at scrap-swatch unit mass.", 2
        ),
        "cell phone" to DecayTimeInfo(
            "cell phone", 100.0, 1000.0, "100 to 1,000+ years",
            "Same concentrated hazard as 'Mobile': lithium cell plus dense heavy metals and rare earths in a small, corrosion-prone package.", 3
        ),
        "electronic" to DecayTimeInfo(
            "electronic", 100.0, 1000.0, "100 to 1,000+ years",
            "Generic small electronics share the 'Electronic Device' profile: mixed boards and plastics leaching metals for centuries.", 3
        ),
        "wine glass" to DecayTimeInfo(
            "wine glass", 1000000.0, 1000000.0, "1 million+ years",
            "Soda-lime drinkware is as permanent as container glass (see 'Glass') — and worse in practice, because it contaminates container-glass recycling when binned wrongly.", 1
        ),
        "glass_container" to DecayTimeInfo(
            "glass_container", 1000000.0, 1000000.0, "1 million+ years",
            "Container glass is chemically inert and effectively permanent — the argument FOR recycling it, not landfilling it.", 1
        ),
        "book" to DecayTimeInfo(
            "book", 0.1, 0.5, "1 month to 6 months",
            "Paper pages rot in weeks; glue binding and covers stretch the tail to months. Keep dry and recycle instead — wet/mouldy books are trash.", 0
        ),
        "banana" to DecayTimeInfo(
            "banana", 0.02, 0.16, "1 week to 2 months",
            "Peels compost in weeks but generate methane in anaerobic landfill — same 'Organic' profile. Compost or use the organics bin.", 0
        ),
        "apple" to DecayTimeInfo(
            "apple", 0.02, 0.16, "1 week to 2 months",
            "Cores compost in weeks but generate methane in anaerobic landfill — same 'Organic' profile. Compost or use the organics bin.", 0
        ),
        "food_waste" to DecayTimeInfo(
            "food_waste", 0.02, 0.16, "1 week to 2 months",
            "Food scraps compost in weeks but are a top landfill-methane source when binned. Compost or use the organics bin.", 0
        )
    )

    private val categoryFallbacks = mapOf(
        "E-Waste" to DecayTimeInfo(
            "Generic E-Waste", 100.0, 1000.0, "100 to 1,000+ years",
            "Electronic waste contains heavy metals and toxic chemicals that endure for centuries. These components severely contaminate soil and groundwater if not properly recycled.", 3
        ),
        "Hazardous" to DecayTimeInfo(
            "Generic Hazardous", 100.0, 1000.0, "100 to 1,000+ years (toxics persist indefinitely)",
            "Hazardous waste is defined by toxicity, not just persistence: assume indefinite soil and groundwater hazard until a specialist stream confirms otherwise.", 3
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
        ),
        // CONTEXT.md name for Trash: identical fallback so Residual callers never miss.
        "Residual" to DecayTimeInfo(
            "Generic Trash", 50.0, 1000.0, "Decades to centuries",
            "General mixed (residual) trash contains persistent synthetic materials that resist natural breakdown. They steadily leak micro-pollutants into the local ecosystem.", 2
        ),
        "Uncertain" to DecayTimeInfo(
            "Unknown Waste", 50.0, 500.0, "Decades to centuries",
            "We could not identify this item: assume persistent synthetics until verified. Check the disposal guide before binning.", 2
        ),
        "Unknown" to DecayTimeInfo(
            "Unknown Waste", 50.0, 500.0, "Decades to centuries",
            "Unidentified waste materials often contain synthetic compounds that do not break down easily. They pose a lasting, unknown threat to natural habitats.", 2
        )
    )

    /**
     * Inputs are trimmed before lookup (consistent with [WasteMapping]); case-insensitive
     * for subclass, case-insensitive with [WasteMapping.RESIDUAL] support for category.
     */
    fun getDecayInfo(subclass: String, category: String): DecayTimeInfo {
        val cleanSub = subclass.trim()
        val cleanCat = category.trim()
        return subclassLookup[cleanSub]
            ?: subclassLookup.entries.find { it.key.equals(cleanSub, ignoreCase = true) }?.value
            ?: categoryFallbacks[cleanCat]
            ?: categoryFallbacks.entries.find { it.key.equals(cleanCat, ignoreCase = true) }?.value
            ?: DecayTimeInfo(
                "Unknown Waste", 50.0, 500.0, "Decades to centuries",
                "Unidentified waste materials often contain synthetic compounds that do not break down easily. They pose a lasting, unknown threat to natural habitats.", 2
            )
    }

    /**
     * Human-scale comparison for a decomposition horizon in years.
     * Anchors (with basis): a season ≈ 0.25 y (astronomical quarter-year); a demographic
     * generation ≈ 25 y (UN World Population Prospects convention, 25–30 y). Earlier
     * buckets ("<1 y → single season", "<50 y → generation") were wrong by factors of
     * ~4 and ~2 respectively — fixed here. Render [DecayTimeInfo.displayText], never raw
     * year math, in animated UI.
     */
    fun getComparison(minYears: Double): String {
        return when {
            minYears < 0.25 -> "Less than a single season"
            minYears < 25.0 -> "Within a single generation"
            minYears < 100.0 -> "Your grandchildren would still see it"
            minYears < 500.0 -> "Outlasts every building standing today"
            minYears < 1000.0 -> "Longer than most civilizations have existed"
            minYears < 1000000.0 -> "Longer than recorded human history"
            else -> "Effectively permanent - it will outlast humanity itself"
        }
    }
}
