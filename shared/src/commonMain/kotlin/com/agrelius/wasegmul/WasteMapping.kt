package com.agrelius.wasegmul

/**
 * Static mapping from each subclass label (as emitted by the TFLite subclass model) to
 * its canonical category, an estimated per-unit weight (kg), and whether it is hazardous.
 *
 * ## Key normalisation
 * Legacy keys mix `Title-Case` (`"Mobile"`), `lowercase spaces` (`"light bulbs"`) and
 * `snake_case` (`"plastic_bag"`). All lookups ([getCategory], [getWeight], [isHazardous],
 * [getMetaOrNull]) trim and lowercase the input, so callers must NOT pre-normalise.
 * New keys should use the exact model label; casing no longer matters.
 *
 * ## Category vocabulary vs CONTEXT.md
 * CONTEXT.md names the top-level tiers `Organic / Recyclable / Hazardous / E-Waste / Residual`.
 * The runtime taxonomy predates that doc and uses **`Trash`** for curbside general waste,
 * i.e. the operational name for CONTEXT.md's `Residual`. [RESIDUAL] is provided as an alias
 * constant; [isKnownCategory] accepts both. A full rename is tracked but deliberately NOT
 * done here (Room columns, analytics history and OFF wire strings all persist `Trash`).
 * There is no standalone `Hazardous` category in the model output; hazardousness is a
 * per-subclass flag ([isHazardous]) and [EcoImpactCalculator] gives hazardous items
 * diversion credit at hazardous-or-better rates regardless of category.
 *
 * ## Curbside truth (documented divergences from naive recyclability)
 * Categories below describe **curbside** collection truth, not specialty streams:
 * - `plastic_bag` / `plastic_wrapper` → [Trash][CategoryTrash]: film jams curbside sorters;
 *   recyclable ONLY via supermarket store-drop-off ([WasteKnowledgeBase] documents this).
 * - `wine glass` → [Trash][CategoryTrash]: drinkware (like Pyrex/ceramics/mirrors)
 *   contaminates container-glass melt batches; NOT curbside recyclable.
 * - `clothing` / `textile` → [Trash][CategoryTrash]: NOT curbside recyclable; donation and
 *   textile-bank specialty streams are documented in [WasteKnowledgeBase]. Both share the
 *   same category so identical fibres earn identical credit; their per-unit weights differ
 *   only because the typical unit differs (whole garment vs scrap swatch).
 *
 * ## Weight provenance
 * Weights are conservative single-unit estimates for the aggregate Eco Impact dashboard
 * only — NOT precise for billing or compliance. Typical-unit basis per entry:
 * - `automobile wastes` 12 kg ≈ one passenger tyre (the observable unit; a whole car is
 *   ~1000 kg and is never a single record).
 * - `clothing` 0.25 kg ≈ one average garment; `textile`/`Textile Trash` 0.10 kg ≈ one scrap
 *   swatch/rag cut.
 * - `Plastic` 0.045 kg ≈ one rigid container; film bags are the separate `plastic_bag`
 *   entry at 0.005 kg — do NOT use `Plastic` for bags (9× overstatement).
 * - Small e-waste per typical device mass (phone 0.18 kg, laptop 1.8 kg); white goods per
 *   appliance class (washer 65 kg, fridge 75 kg, A/C 45 kg).
 * Unmapped labels fall back to [DEFAULT_WEIGHT_KG]; see [getWeight].
 */
object WasteMapping {

    /** Category returned when a subclass label has no mapping entry. */
    const val UNKNOWN = "Unknown"

    /** Category used when confidence is too low to trust either model. */
    const val UNCERTAIN = "Uncertain"

    /**
     * CONTEXT.md name for curbside general waste. Alias of [TRASH]: accepted everywhere a
     * category is validated, but never emitted (runtime still emits `Trash`).
     */
    const val RESIDUAL = "Residual"

    /** Runtime name for curbside general waste (= CONTEXT.md `Residual`). */
    const val TRASH = "Trash"

    /**
     * Single source of truth for the fallback per-unit weight (kg) used when a subclass
     * label is unmapped. Also the default `estimatedWeight` for new [WasteRecord]s —
     * do NOT hard-code `0.05` elsewhere.
     */
    const val DEFAULT_WEIGHT_KG = 0.05

    /**
     * Canonical runtime categories (plus the CONTEXT.md [RESIDUAL] alias and the
     * [UNKNOWN]/[UNCERTAIN] sentinels). Case-insensitive.
     */
    val KNOWN_CATEGORIES: Set<String> =
        setOf("Recyclable", "Organic", "E-Waste", "Hazardous", TRASH, RESIDUAL, UNKNOWN, UNCERTAIN)

    /** True when [category] is a recognised taxonomy value (case-insensitive, trimmed). */
    fun isKnownCategory(category: String): Boolean =
        KNOWN_CATEGORIES.any { it.equals(category.trim(), ignoreCase = true) }

    /**
     * Contract for the two no-decision sentinels (see [MLArbitrator]):
     * - [UNKNOWN]: the label/category is not in the taxonomy at all (unmapped subclass,
     *   rejected category). No guidance beyond generic safe advice.
     * - [UNCERTAIN]: taxonomy is known but confidence is too low to commit. Honest
     *   category-level guidance MAY be shown when the category itself is reliable.
     */
    fun isSentinel(category: String): Boolean =
        category.trim().equals(UNKNOWN, ignoreCase = true) ||
            category.trim().equals(UNCERTAIN, ignoreCase = true)

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
        "Electronic Device" to MaterialMetaData("E-Waste", 0.500, isHazardous = true),
        "Glass" to MaterialMetaData("Recyclable", 0.250),
        "Keyboard" to MaterialMetaData("E-Waste", 0.600),
        "Laptop" to MaterialMetaData("E-Waste", 1.800, isHazardous = true),
        "Metal" to MaterialMetaData("Recyclable", 0.150),
        "Microwave" to MaterialMetaData("E-Waste", 15.0, isHazardous = true),
        "Miscellaneous Trash" to MaterialMetaData(TRASH, 0.030),
        // Li-ion cell inside: puncture/fire risk in collection trucks and MRFs.
        "Mobile" to MaterialMetaData("E-Waste", 0.180, isHazardous = true),
        "Mouse" to MaterialMetaData("E-Waste", 0.100),
        "Organic" to MaterialMetaData("Organic", 0.120),
        "PCB" to MaterialMetaData("E-Waste", 0.080, isHazardous = true),
        "Paper" to MaterialMetaData("Recyclable", 0.010),
        "Plastic" to MaterialMetaData("Recyclable", 0.045),
        // Li-ion cell inside: same fire rationale as Mobile.
        "Player" to MaterialMetaData("E-Waste", 0.300, isHazardous = true),
        // Toner particulate + lead solder on control boards: e-waste handling required.
        "Printer" to MaterialMetaData("E-Waste", 5.0, isHazardous = true),
        "Refrigerator" to MaterialMetaData("E-Waste", 75.0, isHazardous = true),
        "Television" to MaterialMetaData("E-Waste", 12.0, isHazardous = true),
        "Textile Trash" to MaterialMetaData(TRASH, 0.100),
        // Bulk steel/copper appliance with no cell, capacitor bank or refrigerant:
        // recyclable as e-waste/scrap but NOT flagged hazardous (contrast Microwave,
        // whose high-voltage capacitor retains a lethal charge).
        "Washing Machine" to MaterialMetaData("E-Waste", 65.0),
        "automobile wastes" to MaterialMetaData(TRASH, 12.0, isHazardous = true),
        // Curbside Trash (NOT curbside recyclable); donation/textile-bank specialty
        // streams documented in WasteKnowledgeBase. Weight ≈ one average garment.
        "clothing" to MaterialMetaData(TRASH, 0.250),
        "disposable_plastic_cutlery" to MaterialMetaData(TRASH, 0.010),
        "light bulbs" to MaterialMetaData(TRASH, 0.050, isHazardous = true),
        "shoes" to MaterialMetaData("Recyclable", 0.500),
        "styrofoam_cups" to MaterialMetaData(TRASH, 0.005),
        "styrofoam_food_containers" to MaterialMetaData(TRASH, 0.010)
    )

    /**
     * Extended mappings for YOLO object detector classes and common material synonyms.
     */
    val EXTENDED_MAPPING = mapOf(
        "bottle" to MaterialMetaData("Recyclable", 0.040),
        "can" to MaterialMetaData("Recyclable", 0.015),
        "cigarette" to MaterialMetaData(TRASH, 0.001, isHazardous = true),
        "cup" to MaterialMetaData(TRASH, 0.010),
        "electronic" to MaterialMetaData("E-Waste", 0.500, isHazardous = true),
        "food_waste" to MaterialMetaData("Organic", 0.150),
        "glass_container" to MaterialMetaData("Recyclable", 0.250),
        // Film: curbside Trash (jams sorters); store-drop-off specialty stream only.
        "plastic_bag" to MaterialMetaData(TRASH, 0.005),
        "plastic_container" to MaterialMetaData("Recyclable", 0.030),
        "plastic_wrapper" to MaterialMetaData(TRASH, 0.002),
        // Same fibres as clothing; same Trash category so credit is identical.
        // Weight ≈ one scrap swatch (vs a whole garment for clothing).
        "textile" to MaterialMetaData(TRASH, 0.100),
        // Li-ion cell inside: puncture/fire risk, same rationale as Mobile.
        "cell phone" to MaterialMetaData("E-Waste", 0.180, isHazardous = true),
        // Drinkware contaminates container-glass melt: Trash, NOT curbside recyclable.
        "wine glass" to MaterialMetaData(TRASH, 0.200),
        "book" to MaterialMetaData("Recyclable", 0.300),
        "banana" to MaterialMetaData("Organic", 0.120),
        "apple" to MaterialMetaData("Organic", 0.120)
    )

    private val CASE_INSENSITIVE_MAPPING: Map<String, MaterialMetaData> =
        (MAPPING + EXTENDED_MAPPING).entries.associate { (k, v) -> k.lowercase() to v }

    /**
     * Canonicalises [raw] to the exact stored key (case-insensitive, trimmed), or null
     * when the label is unmapped. Prefer this over `MAPPING[key]` (case-sensitive).
     */
    fun canonicalKey(raw: String): String? {
        val needle = raw.trim().lowercase()
        return (MAPPING.keys + EXTENDED_MAPPING.keys).firstOrNull { it.lowercase() == needle }
    }

    /**
     * Fail-closed lookup: the metadata for [subclass], or null when unmapped.
     * Use this when silent [DEFAULT_WEIGHT_KG]/[UNKNOWN] fallbacks would hide bad data;
     * the defaulting wrappers below deliberately keep legacy lenient behaviour and are
     * documented as such.
     */
    fun getMetaOrNull(subclass: String): MaterialMetaData? =
        CASE_INSENSITIVE_MAPPING[subclass.trim().lowercase()]

    /** Returns the canonical category for [subclass], or [UNKNOWN] if it is not mapped. */
    fun getCategory(subclass: String): String =
        getMetaOrNull(subclass)?.category ?: UNKNOWN

    /**
     * Returns the estimated weight (kg) for [subclass].
     * Lenient legacy default: [DEFAULT_WEIGHT_KG] when unmapped. Callers that must
     * distinguish "known 0.05 kg item" from "unmapped guess" should use [getMetaOrNull]
     * (null = unmapped). There is intentionally ONE default constant shared with
     * [WasteRecord] defaults.
     */
    fun getWeight(subclass: String): Double =
        getMetaOrNull(subclass)?.weightKg ?: DEFAULT_WEIGHT_KG

    /**
     * True when the subclass must bypass curbside bins (Li-ion fire risk, lead/mercury,
     * toner, refrigerants, pressurised or chemical residue). Unmapped labels are NOT
     * hazardous (fail-open would mis-route; fail-closed would strand) — documented choice:
     * unknown items get generic safe advice from [WasteKnowledgeBase] instead.
     */
    fun isHazardous(subclass: String): Boolean =
        getMetaOrNull(subclass)?.isHazardous ?: false
}
