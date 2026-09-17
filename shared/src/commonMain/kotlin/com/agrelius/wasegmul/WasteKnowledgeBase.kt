package com.agrelius.wasegmul

/**
 * Authoritative disposal, environmental-impact, and recycling knowledge for every
 * subclass the model can emit (see `subclass_classes.txt`).
 *
 * Every entry is keyed off the exact label string the TFLite subclass model emits, so
 * the app shows real, actionable guidance for 100% of predictions rather than falling
 * through to a generic message.
 *
 * Sources are cited per-entry so the UI can surface provenance to the user.
 */
object WasteKnowledgeBase {

    /**
     * @param category resolved category ("E-Waste", "Recyclable", "Organic", "Trash",
     *                  "Uncertain", or "Unknown").
     * @param subclass  raw label emitted by the subclass model.
     */
    fun getInfo(category: String, subclass: String): WasteInfo {
        val cat = category.trim()
        val rawSub = subclass.trim()
        val canonicalKey = WasteMapping.MAPPING.keys.firstOrNull { it.equals(rawSub, ignoreCase = true) }
        val sub = canonicalKey ?: rawSub

        // Category-level fallback: arbitrator uses UNCERTAIN subclass when only the
        // broad category is reliable. Give honest category guidance instead of generic.
        if (sub.equals(WasteMapping.UNCERTAIN, ignoreCase = true) ||
            sub.equals(WasteMapping.UNKNOWN, ignoreCase = true) ||
            sub.equals(cat, ignoreCase = true) ||
            sub.isEmpty()
        ) {
            getCategoryLevelInfo(cat)?.let { return it }
        }

        if (cat.equals("Uncertain", ignoreCase = true) ||
            cat.equals("Unknown", ignoreCase = true) ||
            cat.isEmpty() ||
            sub.equals(WasteMapping.UNCERTAIN, ignoreCase = true)
        ) {
            return getGeneralInfo(
                "We couldn't identify this item with enough confidence. " +
                    "Please verify manually before disposing of it."
            )
        }

        return when (sub) {
            // ── Hazardous / batteries ──────────────────────────────────────────────
            "Battery" -> WasteInfo(
                disposalGuide = "• HAZARD: Fire risk: never place in regular trash or recycling bins.\n" +
                    "• TAPE: Cover both terminals with clear tape to prevent short circuits.\n" +
                    "• DROP-OFF: Take to a dedicated battery recycling kiosk, electronics store, or household hazardous-waste facility.",
                environmentalImpact = "Prevents toxic heavy metals (Cadmium, Lead, Lithium, Mercury) from leaking into soil and groundwater.",
                recyclingBenefits = "Recovers Cobalt, Lithium, Nickel and steel for new battery production, reducing destructive mining.",
                sources = "EPA - Battery Recycling Guidelines; Call2Recycle"
            )

            // ── Large appliances (white goods) ─────────────────────────────────────
            "Air-Conditioner" -> WasteInfo(
                disposalGuide = "• COOLANT: Contains refrigerants that must be professionally recovered: never vent or dismantle yourself.\n" +
                    "• PICK-UP: Arrange municipal bulky-waste collection or a retailer take-back when buying a replacement.\n" +
                    "• CERTIFIED RECYCLER: Deliver to an R2 / e-Stewards facility so coolant and components are handled safely.",
                environmentalImpact = "Refrigerants (HFCs) have thousands of times the global-warming potential of CO₂ if released; proper recovery is essential.",
                recyclingBenefits = "Steel, copper and aluminum from the casing and compressor are recovered for manufacturing.",
                sources = "EPA Section 608 (Refrigerant Recycling); RAD Program"
            )
            "Refrigerator" -> WasteInfo(
                disposalGuide = "• COOLANT & FOAM: Contains refrigerant and blowing-agent gases that require licensed recovery.\n" +
                    "• TAKE-BACK: Many utility companies and appliance retailers offer collection rebates.\n" +
                    "• CERTIFIED RECYCLER: Use an RAD (Responsible Appliance Disposal) partner.",
                environmentalImpact = "Proper handling stops CFC/HCFC/HFC release, protecting the ozone layer and avoiding potent greenhouse emissions.",
                recyclingBenefits = "Recovers roughly 75% of unit weight as recyclable metal and plastic.",
                sources = "EPA Responsible Appliance Disposal (RAD) Program"
            )
            "Washing Machine" -> WasteInfo(
                disposalGuide = "• BULKY WASTE: Schedule municipal pick-up or use a scrap-metal recycler.\n" +
                    "• RETAILER TAKE-BACK: Often available on delivery of a new unit.\n" +
                    "• DONATE: If functional, donate to a charity or resale shop.",
                environmentalImpact = "Recycling the steel and concrete components avoids the high energy cost of refining virgin ore.",
                recyclingBenefits = "Steel drum and motor copper are highly recyclable and retain value across multiple lifecycles.",
                sources = "Institute of Scrap Recycling Industries (ISRI)"
            )
            "Microwave" -> WasteInfo(
                disposalGuide = "• HAZARD: Contains a capacitor that can retain a lethal charge: do not disassemble.\n" +
                    "• E-WASTE: Drop off at an e-waste center, not curbside recycling.\n" +
                    "• METAL RECYCLER: Some scrap dealers accept the bare metal casing once electronics are removed by a professional.",
                environmentalImpact = "Keeps the magnetron and circuit boards out of landfills where they leach heavy metals.",
                recyclingBenefits = "Steel casing and copper wiring are recovered; circuit boards yield reusable metals.",
                sources = "Earth911; R2 Recycling Standard"
            )
            "Television" -> WasteInfo(
                disposalGuide = "• HAZARD: CRT TVs contain leaded glass; flat-screens contain mercury lamps: handle as e-waste.\n" +
                    "• DATA: Remove any streaming sticks / accounts before disposal.\n" +
                    "• DROP-OFF: Take to an R2/e-Stewards recycler or a retailer TV take-back event.",
                environmentalImpact = "Prevents lead, mercury and flame retardants from contaminating soil and water.",
                recyclingBenefits = "Glass, plastics and precious metals are recovered; reduces demand for virgin materials.",
                sources = "UN Global E-waste Monitor; EPA eCycling"
            )
            "Printer" -> WasteInfo(
                disposalGuide = "• CONSUMABLES: Remove and recycle ink/toner cartridges separately (most manufacturers offer free mail-back).\n" +
                    "• DATA: Clear any stored print jobs or network settings.\n" +
                    "• DROP-OFF: Take to an electronics recycler; many office-supply stores accept working units.",
                environmentalImpact = "Keeps plastic casing and circuit boards out of landfill and prevents ink residue leaching.",
                recyclingBenefits = "Plastics and metals are recovered; cartridges can be refilled and reused multiple times.",
                sources = "EPA Sustainable Materials Management; manufacturer take-back programs"
            )

            // ── Consumer electronics (personal compute devices) ────────────────────
            "Mobile", "Laptop", "Electronic Device", "Player", "electronic", "cell phone" -> WasteInfo(
                disposalGuide = "• DATA: Back up and then securely wipe all personal storage (factory reset / disk wipe).\n" +
                    "• ACCESSORIES: Keep chargers and cables with the device if possible.\n" +
                    "• CERTIFIED RECYCLER: Take to an R2 or e-Stewards certified facility, or a retailer trade-in program.",
                environmentalImpact = "Electronics contain heavy metals and flame retardants that contaminate groundwater if landfilled.",
                recyclingBenefits = "Recovers gold, silver, copper and rare-earth elements, reducing destructive mining.",
                sources = "UN Global E-waste Monitor; e-Stewards / R2 Certification"
            )
            // ── Peripherals & accessories ──────────────────────────────────────────
            "Keyboard", "Mouse" -> WasteInfo(
                disposalGuide = "• BATTERIES: Remove any alkaline or rechargeable batteries before disposal and recycle separately.\n" +
                    "• CABLES: Neatly bundle cords; drop off at electronics retailer recycling bins or municipal e-waste drives.\n" +
                    "• DONATE: If functional, donate to schools, charities, or community computer reuse centers.",
                environmentalImpact = "Prevents durable ABS plastic casing, PCB boards, and solder metals from occupying landfill space.",
                recyclingBenefits = "Recovers high-impact polystyrene, copper wiring, and microswitches for industrial reuse.",
                sources = "EPA Sustainable Materials Management; e-Stewards Standard"
            )
            "Electronic Component", "PCB" -> WasteInfo(
                disposalGuide = "• HAZARD: Circuit boards contain lead solder and brominated flame retardants: never landfill.\n" +
                    "• E-WASTE: Drop off at a certified electronics recycler that accepts bare boards.\n" +
                    "• BULK: For large volumes, use a specialist board buyer/refiner.",
                environmentalImpact = "Prevents lead and brominated compounds from leaching into the environment.",
                recyclingBenefits = "Printed circuit boards are one of the richest sources of recoverable gold, copper, palladium and silver.",
                sources = "Geological Survey Mineral Commodity Summaries; R2 Standard"
            )

            // ── Recyclables ────────────────────────────────────────────────────────
            "Plastic", "bottle", "plastic_container" -> WasteInfo(
                disposalGuide = "• RINSE: Remove food residue and let dry.\n" +
                    "• CHECK: Confirm the Resin Identification Code: #1 (PET), #2 (HDPE) and #5 (PP) are the most widely accepted.\n" +
                    "• BINS: Place in your yellow/blue recycling bin; keep caps on unless local rules say otherwise.",
                environmentalImpact = "Diverts material from a 450-year landfill decomposition cycle and keeps it out of waterways.",
                recyclingBenefits = "Cuts petroleum demand for virgin plastic and uses far less energy than producing new resin.",
                sources = "Sustainable Packaging Coalition; How2Recycle"
            )
            "Paper", "Cardboard", "book" -> WasteInfo(
                disposalGuide = "• DRY: Wet paper and cardboard cannot be recycled: keep them dry.\n" +
                    "• FLATTEN: Break down boxes to save transport space and emissions.\n" +
                    "• REMOVE: Strip off plastic tape, bubble wrap and food-soiled portions.",
                environmentalImpact = "Saves trees and cuts the methane released when organics decompose in landfill.",
                recyclingBenefits = "Recycled paper uses roughly 40% less energy and far less water than virgin wood pulp.",
                sources = "American Forest & Paper Association (AF&PA)"
            )
            "Metal", "can" -> WasteInfo(
                disposalGuide = "• CLEAN: Rinse out food, paint or chemical residue.\n" +
                    "• TYPES: Aluminum and steel are infinitely recyclable: a magnet will tell them apart (steel sticks).\n" +
                    "• SORT: In single-stream systems, keep loose metal from tangling in sorting machines.",
                environmentalImpact = "Mining ore is up to 95% more energy-intensive than recycling existing metal.",
                recyclingBenefits = "Metals retain their structural quality through unlimited recycling loops.",
                sources = "International Aluminum Institute; Institute of Scrap Recycling Industries"
            )
            "Glass", "glass_container", "wine glass" -> WasteInfo(
                disposalGuide = "• RINSE: Wash away sugars or oils.\n" +
                    "• SORT: Separate by color where your municipality requires it.\n" +
                    "• NO CERAMICS: Pyrex, ceramics and mirrors contaminate glass-melt batches: bin them as trash.",
                environmentalImpact = "Glass takes up to a million years to decompose; recycling is the only sustainable path.",
                recyclingBenefits = "Cullet (crushed glass) lowers furnace temperatures, saving energy and cutting CO₂ emissions.",
                sources = "Glass Packaging Institute (GPI)"
            )
            "clothing", "shoes" -> WasteInfo(
                disposalGuide = "• REUSE: Donate wearable items to charity shops or textile banks.\n" +
                    "• RETAILER: Many fashion brands now accept old textiles for recycling in-store.\n" +
                    "• RAGS: Even worn-out textiles can be recycled into insulation or cleaning cloths: don't bin them.",
                environmentalImpact = "Keeps textiles out of landfill where they release methane during decomposition.",
                recyclingBenefits = "Reduces water and pesticide demand of virgin cotton and lowers synthetic fiber production.",
                sources = "Council for Textile Recycling; Ellen MacArthur Foundation"
            )

            // ── Organic ────────────────────────────────────────────────────────────
            "Organic", "food_waste", "banana", "apple" -> WasteInfo(
                disposalGuide = "• COMPOST: Ideal for garden soil enrichment.\n" +
                    "• NO PLASTIC: Remove produce stickers, rubber bands and plastic liners.\n" +
                    "• BINS: Use your dedicated brown/green organic-waste bin if available.",
                environmentalImpact = "Diverts organic waste from landfill where it produces methane, a potent greenhouse gas.",
                recyclingBenefits = "Returns nutrients to soil and supports local biodiversity and food production.",
                sources = "US Composting Council"
            )

            // ── Trash / hard-to-recycle ────────────────────────────────────────────
            "Miscellaneous Trash" -> WasteInfo(
                disposalGuide = "• SORT: Check each item against the other categories first: most 'trash' is actually recyclable.\n" +
                    "• COMPACT: Crush items to reduce landfill volume.\n" +
                    "• BINS: Place genuinely non-recyclable residue in the general-waste bin.",
                environmentalImpact = "Correct sorting upstream is the single biggest lever for reducing landfill mass.",
                recyclingBenefits = "Properly binned recyclables avoid contamination that would spoil whole batches.",
                sources = "EPA Sustainable Materials Management"
            )
            "Textile Trash", "textile" -> WasteInfo(
                disposalGuide = "• TEXTILE BANK: Even damaged fabric can be recycled at textile collection points.\n" +
                    "• DONATE: Give re-usable items to charity.\n" +
                    "• TRASH: Only non-recyclable, soiled textiles go to general waste.",
                environmentalImpact = "Diverts textiles from landfill, avoiding methane from decomposition.",
                recyclingBenefits = "Recovers fibers for insulation, upholstery padding and cleaning rags.",
                sources = "Council for Textile Recycling"
            )
            "disposable_plastic_cutlery" -> WasteInfo(
                disposalGuide = "• TRASH: Most curbside programs do NOT accept cutlery (wrong shape for sorters, often #6 PS).\n" +
                    "• REUSE: Wash and reuse if possible before disposal.\n" +
                    "• REDUCE: Switch to reusable or compostable cutlery where you can.",
                environmentalImpact = "Single-use plastic cutlery is a leading contributor to plastic pollution in waterways.",
                recyclingBenefits = "Limited recyclability, but refusing and reducing has the highest positive impact.",
                sources = "Ocean Conservancy; National Geographic Plastic Toolkit"
            )
            "plastic_bag", "plastic_wrapper" -> WasteInfo(
                disposalGuide = "• STORE DROP-OFF: Plastic film and grocery bags jam curbside sorting machinery: take to supermarket collection bins.\n" +
                    "• REUSE: Use plastic bags as small trash can liners or pet waste bags before final disposal.\n" +
                    "• TRASH: If soiled or unrecyclable locally, discard with general household trash.",
                environmentalImpact = "Plastic film is a major marine hazard; proper collection prevents waterway entanglement.",
                recyclingBenefits = "Collected film plastic is pelletized into composite lumber (e.g. decking and park benches).",
                sources = "EPA Plastic Film Recycling; How2Recycle Store Drop-Off"
            )
            "cigarette" -> WasteInfo(
                disposalGuide = "• FIRE SAFETY: Fully extinguish in an ashtray or water before disposal to avoid bin fires.\n" +
                    "• TRASH: Butts are non-biodegradable (cellulose acetate): place securely in general trash.\n" +
                    "• SPECIALIST: Look for community cigarette waste collection receptacles where available.",
                environmentalImpact = "Filters leach nicotine, arsenic, and microplastics into stormwater runoff and aquatic ecosystems.",
                recyclingBenefits = "Specialist programs separate cellulose acetate for industrial plastic pelletization.",
                sources = "Truth Initiative; Ocean Conservancy; TerraCycle"
            )
            "cup" -> WasteInfo(
                disposalGuide = "• PLASTIC/WAX LINING: Most disposable paper cups have a polyethylene moisture barrier: check if your city accepts lined cups.\n" +
                    "• SEPARATE: Remove plastic lids and cardboard sleeves; sleeves can usually go in paper recycling.\n" +
                    "• TRASH: If unsure whether lined cups are recyclable locally, place cup in general trash.",
                environmentalImpact = "Billions of single-use cups end up in landfills annually; reusable travel mugs avoid this waste.",
                recyclingBenefits = "Specialized mills can repulp poly-coated cupstock into paper napkins and corrugated boxes.",
                sources = "Foodservice Packaging Institute; Earth911"
            )
            "styrofoam_cups", "styrofoam_food_containers" -> WasteInfo(
                disposalGuide = "• TRASH: Usually NOT accepted in curbside recycling.\n" +
                    "• CLEAN: If your city accepts EPS, it must be completely clean and dry.\n" +
                    "• DROP-OFF: Look for dedicated foam #6 drop-off sites; otherwise bin as trash.",
                environmentalImpact = "EPS is extremely persistent in ocean ecosystems and breaks into harmful microplastics.",
                recyclingBenefits = "Technically recyclable, but its low density makes collection transport energy-inefficient.",
                sources = "Ocean Conservancy; Earth911 EPS recycling"
            )
            "light bulbs" -> WasteInfo(
                disposalGuide = "• FRAGILE: Wrap in paper to prevent breakage and injury.\n" +
                    "• CFL/FLUORESCENT: Contain mercury: take to a hazardous-waste drop-off, never the regular bin.\n" +
                    "• LED/INCANDESCENT: Check local rules; LEDs can often be recycled as e-waste, incandescents as trash.",
                environmentalImpact = "Proper disposal prevents mercury vapor release into the atmosphere.",
                recyclingBenefits = "Glass, metal and (in LEDs) electronic components can be recovered safely.",
                sources = "EPA Fluorescent Bulb Recycling; ENERGY STAR"
            )
            "automobile wastes" -> WasteInfo(
                disposalGuide = "• HAZARD: May contain oils, fuels, batteries or brake fluid: handle as hazardous waste.\n" +
                    "• PARTS: Batteries, tires and oil filters have dedicated recycling streams.\n" +
                    "• FACILITY: Take to an automotive recycler or municipal hazardous-waste site.",
                environmentalImpact = "Prevents motor oil, antifreeze and heavy metals from contaminating soil and water.",
                recyclingBenefits = "Recovers steel, aluminum, rubber and reusable parts, cutting virgin manufacturing demand.",
                sources = "EPA Automotive Waste; Automotive Recyclers Association"
            )

            // ── Fallback (should no longer trigger for the 30 known labels) ─────────
            else -> getGeneralInfo("Follow local municipal guidelines for '$subclass'.")
        }
    }

    /** Generic fallback used for unknown / uncertain items. */
    private fun getGeneralInfo(customGuide: String) = WasteInfo(
        disposalGuide = customGuide,
        environmentalImpact = "Correct identification is the first step in the Circular Economy.",
        recyclingBenefits = "Individual action scales to global environmental resilience.",
        sources = "agrelius Sustainability Research"
    )

    private fun getCategoryLevelInfo(category: String): WasteInfo? = when (category) {
        "E-Waste" -> WasteInfo(
            disposalGuide = "• CATEGORY-LEVEL: Specific type unclear: treat as e-waste.\n" +
                "• DO NOT bin in trash/recycling. Take to a certified e-waste drop-off (R2/e-Stewards).",
            environmentalImpact = "E-waste leaches heavy metals if landfilled.",
            recyclingBenefits = "Recovers metals and prevents toxic release.",
            sources = "EPA eCycling; UN Global E-waste Monitor"
        )
        "Recyclable" -> WasteInfo(
            disposalGuide = "• CATEGORY-LEVEL: Rinse, dry, and place in recycling bin.\n" +
                "• Check local resin/sorting rules for exact acceptance.",
            environmentalImpact = "Diverts material from landfill.",
            recyclingBenefits = "Saves energy vs virgin production.",
            sources = "EPA Sustainable Materials Management"
        )
        "Organic" -> WasteInfo(
            disposalGuide = "• CATEGORY-LEVEL: Compost or use organic bin.\n• Remove plastics/stickers.",
            environmentalImpact = "Avoids landfill methane.",
            recyclingBenefits = "Returns nutrients to soil.",
            sources = "US Composting Council"
        )
        "Trash" -> WasteInfo(
            disposalGuide = "• CATEGORY-LEVEL: Place in general waste.\n• Double-check it is not recyclable first.",
            environmentalImpact = "Landfill minimized by correct sorting.",
            recyclingBenefits = "Avoids contaminating recyclables.",
            sources = "EPA Sustainable Materials Management"
        )
        else -> null
    }
}
