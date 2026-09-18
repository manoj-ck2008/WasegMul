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
 *
 * ## i18n status (documented tech debt)
 * All copy below is hard-coded English, grouped one `when` branch per canonical label so
 * extraction to platform string resources is mechanical (branch → string key, bullets →
 * format args). Until extracted, non-English locales receive English guidance — tracked.
 */
object WasteKnowledgeBase {

    /**
     * Canonical label lookup over BOTH [WasteMapping.MAPPING] and
     * [WasteMapping.EXTENDED_MAPPING] (case-insensitive). The old code only canonicalised
     * MAPPING, so every lowercase EXTENDED label (e.g. a capitalised `"Bottle"`) fell
     * through to the generic `else` branch — fixed here.
     */
    private val CANONICAL_LABELS: Map<String, String> =
        (WasteMapping.MAPPING.keys + WasteMapping.EXTENDED_MAPPING.keys)
            .associateBy { it.lowercase() }

    /** Canonicalises [raw] to the stored label form (case-insensitive, trimmed). */
    fun canonicalLabel(raw: String): String {
        val needle = raw.trim().lowercase()
        return CANONICAL_LABELS[needle] ?: raw.trim()
    }

    /**
     * @param category resolved category ("E-Waste", "Recyclable", "Organic", "Trash",
     *                  "Hazardous", "Residual" (= Trash, CONTEXT.md name), "Uncertain", or "Unknown").
     * @param subclass  raw label emitted by the subclass model.
     */
    fun getInfo(category: String, subclass: String): WasteInfo {
        val cat = category.trim()
        val sub = canonicalLabel(subclass)

        // Category-level fallback: arbitrator uses UNCERTAIN subclass when only the
        // broad category is reliable. Give honest category guidance instead of generic.
        if (sub.equals(WasteMapping.UNCERTAIN, ignoreCase = true) ||
            sub.equals(WasteMapping.UNKNOWN, ignoreCase = true) ||
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
                disposalGuide = "• HAZARD: CRT TVs contain leaded glass; CCFL-backlit LCDs (older flat panels) contain mercury lamps — handle as e-waste. Modern LED/OLED panels contain no mercury but still require e-waste handling.\n" +
                    "• DATA: Remove any streaming sticks / accounts before disposal.\n" +
                    "• DROP-OFF: Take to an R2/e-Stewards recycler or a retailer TV take-back event.",
                environmentalImpact = "Prevents lead, mercury (CCFL units) and flame retardants from contaminating soil and water.",
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
            // NOTE: reconciled with WasteMapping hazard flags — every branch member below
            // carries a Li-ion cell (Mobile/Laptop/Player/Electronic Device/cell phone) and
            // is hazardous=true there. The guide therefore leads with fire handling.
            "Mobile", "Laptop", "Electronic Device", "Player", "electronic", "cell phone" -> WasteInfo(
                disposalGuide = "• FIRE SAFETY FIRST: Contains a lithium-ion battery: never puncture, crush or bin it — damaged cells ignite in trucks and sorting plants.\n" +
                    "• DATA: Back up and then securely wipe all personal storage (factory reset / disk wipe).\n" +
                    "• CERTIFIED RECYCLER: Take to an R2 or e-Stewards certified facility, a retailer trade-in, or a household battery/e-waste drop-off.",
                environmentalImpact = "Electronics contain heavy metals and flame retardants that contaminate groundwater if landfilled; battery fires release toxic fumes.",
                recyclingBenefits = "Recovers gold, silver, copper and rare-earth elements, reducing destructive mining.",
                sources = "UN Global E-waste Monitor; e-Stewards / R2 Certification; EPA Used Lithium-Ion Battery guidance"
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
            // NOTE: reconciled with PackagingWasteMapper — PVC (#3) and PS (#6) map to
            // Trash/Plastic there, so this bin-recyclable branch explicitly EXCLUDES them.
            "Plastic", "bottle", "plastic_container" -> WasteInfo(
                disposalGuide = "• RINSE: Remove food residue and let dry.\n" +
                    "• CHECK: Confirm the Resin Identification Code: #1 (PET), #2 (HDPE) and #5 (PP) are the most widely accepted. #3 (PVC) and #6 (PS) are NOT curbside recyclable — bin them as trash.\n" +
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
                environmentalImpact = "Recycling aluminium saves up to ~95% of the energy needed for primary production from ore — i.e. mining and refining virgin ore uses roughly 20× more energy (International Aluminium Institute).",
                recyclingBenefits = "Metals retain their structural quality through unlimited recycling loops.",
                sources = "International Aluminium Institute (IAI); Institute of Scrap Recycling Industries"
            )
            // Container glass ONLY. Drinkware (wine glass), Pyrex, ceramics and mirrors
            // contaminate glass-melt batches and are Trash — see the "wine glass" branch.
            "Glass", "glass_container" -> WasteInfo(
                disposalGuide = "• RINSE: Wash away sugars or oils.\n" +
                    "• SORT: Separate by color where your municipality requires it.\n" +
                    "• CONTAINERS ONLY: Bottles and jars. Wine glasses, Pyrex, ceramics and mirrors contaminate glass-melt batches: bin them as trash.",
                environmentalImpact = "Glass takes up to a million years to decompose; recycling is the only sustainable path.",
                recyclingBenefits = "Cullet (crushed glass) lowers furnace temperatures, saving energy and cutting CO₂ emissions.",
                sources = "Glass Packaging Institute (GPI)"
            )
            // Drinkware truth (reconciled with WasteMapping: wine glass → Trash): lead/crystal
            // content and a different melt point contaminate container-glass recycling.
            "wine glass" -> WasteInfo(
                disposalGuide = "• TRASH: Drinking glasses are NOT container glass: bin with general waste.\n" +
                    "• WRAP: Wrap shards in paper to protect handlers.\n" +
                    "• REUSE: Intact glasses are ideal for donation or reuse — never the recycling bin.",
                environmentalImpact = "One drinking glass in a container-glass batch can spoil the melt and landfill the whole load.",
                recyclingBenefits = "Keeping drinkware out preserves the recyclability of true container glass.",
                sources = "Glass Packaging Institute (GPI)"
            )
            "shoes" -> WasteInfo(
                disposalGuide = "• REUSE: Donate wearable items to charity shops or textile banks.\n" +
                    "• RETAILER: Many fashion brands now accept old textiles for recycling in-store.\n" +
                    "• RAGS: Even worn-out textiles can be recycled into insulation or cleaning cloths: don't bin them.",
                environmentalImpact = "Keeps textiles out of landfill where they release methane during decomposition.",
                recyclingBenefits = "Reduces water and pesticide demand of virgin cotton and lowers synthetic fiber production.",
                sources = "Council for Textile Recycling; Ellen MacArthur Foundation"
            )
            // Curbside truth (reconciled with WasteMapping: clothing → Trash): NOT curbside
            // recyclable — but donation/textile-bank specialty streams keep it out of landfill.
            "clothing" -> WasteInfo(
                disposalGuide = "• NOT CURBSIDE: Clothing is not accepted in curbside recycling: use general waste OR a specialty stream below.\n" +
                    "• DONATE: Give wearable items to charity shops or textile banks.\n" +
                    "• RETAILER: Many fashion brands accept old textiles for recycling in-store.",
                environmentalImpact = "Keeps textiles out of landfill where synthetics shed microplastics and organics release methane.",
                recyclingBenefits = "Reuse beats recycling: extends garment life; unwearable fibres become insulation or rags.",
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
            else -> getGeneralInfo("Follow local municipal guidelines for '$sub'.")
        }
    }

    /** Generic fallback used for unknown / uncertain items. */
    private fun getGeneralInfo(customGuide: String) = WasteInfo(
        disposalGuide = customGuide,
        environmentalImpact = "Correct identification is the first step in the Circular Economy.",
        recyclingBenefits = "Individual action scales to global environmental resilience.",
        // Real provenance: no self-citation. The generic fallback carries no material
        // claims, so it cites the programme it derives from rather than inventing authority.
        sources = "EPA Sustainable Materials Management (SMM)"
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
        // CONTEXT.md name for Trash — identical guidance, so Residual callers never get null.
        "Residual" -> WasteInfo(
            disposalGuide = "• CATEGORY-LEVEL: Place in general (residual) waste.\n• Double-check it is not recyclable first.",
            environmentalImpact = "Landfill minimized by correct sorting.",
            recyclingBenefits = "Avoids contaminating recyclables.",
            sources = "EPA Sustainable Materials Management"
        )
        "Hazardous" -> WasteInfo(
            disposalGuide = "• CATEGORY-LEVEL: HAZARDOUS — never place in trash or recycling bins.\n" +
                "• CONTAIN: Seal, label, and keep dry and away from heat.\n" +
                "• DROP-OFF: Take to a household hazardous-waste facility or certified collection event.",
            environmentalImpact = "Hazardous waste leaches toxics and starts collection fires when binned.",
            recyclingBenefits = "Specialist channels recover materials that curbside streams cannot handle safely.",
            sources = "EPA Household Hazardous Waste; Call2Recycle"
        )
        else -> null
    }
}
