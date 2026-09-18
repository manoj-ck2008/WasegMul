package com.agrelius.wasegmul

import com.agrelius.wasegmul.network.OffPackagingComponentDto
import com.agrelius.wasegmul.network.OffProductDto
import kotlinx.serialization.Serializable

/**
 * Resolved packaging component with human-readable descriptions,
 * WasegMul domain Category and Subclass, disposal instructions,
 * and optional weight in grams.
 *
 * @param weightGrams component mass in **grams** (OFF convention — NOT kg; divide by
 *   1000 for [WasteRecord.estimatedWeight]). Null = unknown, never zero-fill.
 * @param disposalAction closed vocab [PackagingWasteMapper.DisposalAction].
 * @param category domain category; validated against [WasteMapping.isKnownCategory].
 */
@Serializable
data class ResolvedPackagingComponent(
    val shape: String,
    val material: String,
    val category: String,
    val subclass: String,
    val disposalAction: String,
    val weightGrams: Double? = null
)

/**
 * Maps Open Food Facts packaging taxonomy identifiers to WasegMul domain categories and subclasses.
 *
 * Provides primary material mapping, shape-based fallback heuristics, taxonomy label humanization,
 * and disposal action resolution ("Recycle", "Discard", "Compost").
 *
 * ## Specialty-stream truth (reconciled with WasteKnowledgeBase)
 * Curbside categories below are deliberately conservative for materials that need specialty
 * mills or industrial composting — documented per entry rather than silently "recyclable":
 * - Tetra Pak / composites (`tetra-pak`, `composite-material`, `c-pap`, `multilayer`) →
 *   Trash: paper-plastic-foil laminates need specialty mills unavailable curbside.
 * - Film bags (`bag`) → Trash: store-drop-off only (jams curbside sorters).
 * - Bioplastics / PLA → Trash: industrial-compost-only; they CONTAMINATE both organics
 *   streams and PET recycling when binned hopefully.
 * - PVC (#3) / PS (#6) / EPS / "other 7" → Trash/Plastic: excluded from the KB's
 *   bin-recyclable Plastic branch (which now says so explicitly).
 */
object PackagingWasteMapper {

    /**
     * Closed vocabulary for disposal actions (storage/wire stays `String` for OFF + Room
     * compat; validate with [DisposalAction.isKnown] instead of string literals).
     */
    object DisposalAction {
        const val RECYCLE = "Recycle"
        const val DISCARD = "Discard"
        const val COMPOST = "Compost"
        val ALL: Set<String> = setOf(RECYCLE, DISCARD, COMPOST)
        fun isKnown(action: String?): Boolean =
            action != null && ALL.any { it.equals(action.trim(), ignoreCase = true) }
    }

    /**
     * Absurd-weight guard: component weights arrive in **grams** (see [OffModels] KDoc).
     * Anything above 50 kg for a single packaging component is a unit error (kg entered as
     * g would read 1000×) and resolves to null (unknown) rather than corrupting totals.
     */
    const val MAX_COMPONENT_WEIGHT_GRAMS = 50_000.0

    /**
     * Special human-readable names for standard taxonomy abbreviations and resin codes.
     */
    private val SPECIAL_HUMAN_NAMES = mapOf(
        "pet-1" to "PET",
        "pet" to "PET",
        "pete" to "PET",
        "rpet" to "rPET",
        "hdpe-2" to "HDPE",
        "hdpe" to "HDPE",
        "pe-hd" to "HDPE",
        "pvc-3" to "PVC",
        "pvc" to "PVC",
        "ldpe-4" to "LDPE",
        "ldpe" to "LDPE",
        "pe-ld" to "LDPE",
        "pp-5" to "PP",
        "pp" to "PP",
        "ps-6" to "PS",
        "ps" to "PS",
        "eps" to "EPS",
        "expanded-polystyrene" to "Expanded Polystyrene",
        "other-plastics-7" to "Other Plastics",
        "other-plastic-7" to "Other Plastics",
        "o-7" to "Other Plastics",
        "pla" to "PLA",
        "tetra-pak" to "Tetra Pak",
        "tetrapak" to "Tetra Pak",
        "c-pap" to "Paper Composite",
        "alu" to "Aluminium",
        "tin-plate" to "Tinplate"
    )

    /**
     * Static mapping from Open Food Facts material taxonomy IDs to WasegMul Pair(Category, Subclass).
     * Covers common resin codes (1-7), glass, metals, paper, cardboard, composites, and natural materials.
     */
    val MATERIAL_MAPPING: Map<String, Pair<String, String>> = mapOf(
        // Plastics & Polymers (Resin Codes 1-7)
        "en:pet-1" to Pair("Recyclable", "Plastic"),
        "en:pet" to Pair("Recyclable", "Plastic"),
        "en:pete" to Pair("Recyclable", "Plastic"),
        "en:rpet" to Pair("Recyclable", "Plastic"),
        "en:hdpe-2" to Pair("Recyclable", "Plastic"),
        "en:hdpe" to Pair("Recyclable", "Plastic"),
        "en:pe-hd" to Pair("Recyclable", "Plastic"),
        "en:pvc-3" to Pair("Trash", "Plastic"),
        "en:pvc" to Pair("Trash", "Plastic"),
        "en:ldpe-4" to Pair("Recyclable", "Plastic"),
        "en:ldpe" to Pair("Recyclable", "Plastic"),
        "en:pe-ld" to Pair("Recyclable", "Plastic"),
        "en:pp-5" to Pair("Recyclable", "Plastic"),
        "en:pp" to Pair("Recyclable", "Plastic"),
        "en:ps-6" to Pair("Trash", "Plastic"),
        "en:ps" to Pair("Trash", "Plastic"),
        "en:expanded-polystyrene" to Pair("Trash", "Plastic"),
        "en:eps" to Pair("Trash", "Plastic"),
        "en:other-plastics-7" to Pair("Trash", "Plastic"),
        "en:other-plastic-7" to Pair("Trash", "Plastic"),
        "en:o-7" to Pair("Trash", "Plastic"),
        "en:plastic" to Pair("Recyclable", "Plastic"),
        // Industrial-compost-only: contaminates organics AND PET streams → Trash (see class KDoc).
        "en:bioplastic" to Pair("Trash", "Plastic"),
        "en:pla" to Pair("Trash", "Plastic"),
        "en:biodegradable-plastic" to Pair("Trash", "Plastic"),

        // Glass
        "en:glass" to Pair("Recyclable", "Glass"),
        "en:clear-glass" to Pair("Recyclable", "Glass"),
        "en:green-glass" to Pair("Recyclable", "Glass"),
        "en:brown-glass" to Pair("Recyclable", "Glass"),
        "en:amber-glass" to Pair("Recyclable", "Glass"),
        "en:coloured-glass" to Pair("Recyclable", "Glass"),
        "en:colorless-glass" to Pair("Recyclable", "Glass"),

        // Metals
        "en:aluminium" to Pair("Recyclable", "Metal"),
        "en:aluminum" to Pair("Recyclable", "Metal"),
        "en:alu" to Pair("Recyclable", "Metal"),
        "en:steel" to Pair("Recyclable", "Metal"),
        "en:tin" to Pair("Recyclable", "Metal"),
        "en:tinplate" to Pair("Recyclable", "Metal"),
        "en:tin-plate" to Pair("Recyclable", "Metal"),
        "en:metal" to Pair("Recyclable", "Metal"),
        "en:iron" to Pair("Recyclable", "Metal"),

        // Paper & Cardboard
        "en:paper" to Pair("Recyclable", "Paper"),
        "en:cardboard" to Pair("Recyclable", "Cardboard"),
        "en:corrugated-cardboard" to Pair("Recyclable", "Cardboard"),
        "en:kraft-paper" to Pair("Recyclable", "Paper"),
        "en:paperboard" to Pair("Recyclable", "Cardboard"),
        "en:carton" to Pair("Recyclable", "Cardboard"),

        // Composites & Multilayer (specialty mills only → curbside Trash; see class KDoc)
        "en:tetra-pak" to Pair("Trash", "Miscellaneous Trash"),
        "en:tetrapak" to Pair("Trash", "Miscellaneous Trash"),
        "en:composite-material" to Pair("Trash", "Miscellaneous Trash"),
        "en:composite" to Pair("Trash", "Miscellaneous Trash"),
        "en:c-pap" to Pair("Trash", "Miscellaneous Trash"),
        "en:multilayer" to Pair("Trash", "Plastic"),

        // Natural & Organic Materials
        // NOTE: en:cotton → Trash/Textile Trash is consistent with WasteMapping clothing →
        // Trash (same Trash category ⇒ identical Eco Impact credit; specialty donation
        // streams documented in WasteKnowledgeBase). Cotton is not compostable curbside
        // (dyes/finishes), hence NOT Organic.
        "en:wood" to Pair("Organic", "Organic"),
        "en:cork" to Pair("Organic", "Organic"),
        "en:bamboo" to Pair("Organic", "Organic"),
        "en:cotton" to Pair("Trash", "Textile Trash"),
        "en:jute" to Pair("Organic", "Organic"),
        "en:ceramic" to Pair("Trash", "Miscellaneous Trash")
    )

    /**
     * Fallback mapping from Open Food Facts shape taxonomy IDs to best-guess Pair(Category, Subclass)
     * when explicit material information is missing.
     *
     * Hazard-aware and ambiguity-aware (see class KDoc):
     * - `bottle` → Trash: shape alone cannot determine the melt (PET vs glass mislabels
     *   corrupt analytics either way); material tags resolve correctly when present.
     * - `aerosol` → Trash/Metal: pressurised containers need hazardous handling, never
     *   curbside-single-stream assumptions.
     * - `barrel`/`drum` → Trash/Metal: chemical-residue risk dominates.
     * - `bag` → Trash: film is store-drop-off only.
     */
    val SHAPE_FALLBACK: Map<String, Pair<String, String>> = mapOf(
        "en:bottle" to Pair("Trash", "Miscellaneous Trash"),
        "en:can" to Pair("Recyclable", "Metal"),
        "en:box" to Pair("Recyclable", "Cardboard"),
        "en:jar" to Pair("Recyclable", "Glass"),
        "en:carton" to Pair("Recyclable", "Cardboard"),
        "en:tub" to Pair("Recyclable", "Plastic"),
        "en:pot" to Pair("Recyclable", "Plastic"),
        "en:tray" to Pair("Recyclable", "Plastic"),
        "en:bag" to Pair("Trash", "Plastic"),
        "en:pouch" to Pair("Trash", "Plastic"),
        "en:wrapper" to Pair("Trash", "Plastic"),
        "en:film" to Pair("Trash", "Plastic"),
        "en:sachet" to Pair("Trash", "Miscellaneous Trash"),
        "en:packet" to Pair("Trash", "Miscellaneous Trash"),
        "en:cup" to Pair("Trash", "Plastic"),
        "en:lid" to Pair("Recyclable", "Plastic"),
        "en:cap" to Pair("Recyclable", "Plastic"),
        "en:cork" to Pair("Organic", "Organic"),
        "en:stopper" to Pair("Organic", "Organic"),
        "en:aerosol" to Pair("Trash", "Metal"),
        "en:tin" to Pair("Recyclable", "Metal"),
        "en:envelope" to Pair("Recyclable", "Paper"),
        "en:sheet" to Pair("Recyclable", "Paper"),
        "en:clamshell" to Pair("Recyclable", "Plastic"),
        "en:blister-pack" to Pair("Trash", "Miscellaneous Trash"),
        "en:tube" to Pair("Trash", "Plastic"),
        "en:punnet" to Pair("Recyclable", "Plastic"),
        "en:barrel" to Pair("Trash", "Metal"),
        "en:drum" to Pair("Trash", "Metal"),
        "en:keg" to Pair("Recyclable", "Metal"),
        "en:flagon" to Pair("Recyclable", "Glass"),
        "en:vial" to Pair("Recyclable", "Glass"),
        "en:ampoule" to Pair("Recyclable", "Glass")
    )

    /**
     * Finds the Category and Subclass mapping for an OFF material tag ID,
     * supporting tags with or without the language prefix — INCLUDING non-English
     * prefixes: `fr:pet-1` retries as `en:pet-1` (keys are English-canonical; OFF serves
     * the same taxonomy stem under every locale prefix).
     */
    fun getMaterialMapping(tag: String?): Pair<String, String>? {
        if (tag.isNullOrBlank()) return null
        val clean = tag.trim().lowercase()
        val withEn = if (clean.contains(':')) clean else "en:$clean"
        val withoutEn = clean.substringAfter(':')
        return MATERIAL_MAPPING[withEn]
            ?: MATERIAL_MAPPING[clean]
            ?: MATERIAL_MAPPING[withoutEn]
            // Non-English prefix retry: fr:pet-1 / de:pet-1 → en:pet-1.
            ?: MATERIAL_MAPPING["en:$withoutEn"]
    }

    /**
     * Finds the fallback Category and Subclass mapping for an OFF shape tag ID,
     * supporting tags with or without the language prefix (same non-English retry as
     * [getMaterialMapping]).
     */
    fun getShapeFallback(tag: String?): Pair<String, String>? {
        if (tag.isNullOrBlank()) return null
        val clean = tag.trim().lowercase()
        val withEn = if (clean.contains(':')) clean else "en:$clean"
        val withoutEn = clean.substringAfter(':')
        return SHAPE_FALLBACK[withEn]
            ?: SHAPE_FALLBACK[clean]
            ?: SHAPE_FALLBACK[withoutEn]
            ?: SHAPE_FALLBACK["en:$withoutEn"]
    }

    /**
     * Converts a raw taxonomy tag (e.g. "en:pet-1", "en:clear-glass") into a clean,
     * human-readable label (e.g. "PET", "Clear Glass").
     *
     * @param tag Taxonomy identifier string.
     * @return Humanized title-cased or acronym string.
     */
    fun humanizeTag(tag: String?): String {
        if (tag.isNullOrBlank()) return ""
        val raw = tag.substringAfter(':').trim().lowercase()
        SPECIAL_HUMAN_NAMES[raw]?.let { return it }

        return raw.split('-', '_', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }

    /**
     * Maps an Open Food Facts recycling tag to a WasegMul disposal action:
     * "Recycle", "Discard", or "Compost" (see [DisposalAction]).
     *
     * Matching is token-based with word boundaries ("bin" matches `en:bin` but NOT
     * "combine"), and negations are checked FIRST ("do-not-recycle" contains "recycle"
     * as a substring — checking recycle first misroutes it to Recycle).
     *
     * @param recyclingTag Recycling taxonomy ID (e.g. "en:recycle", "en:discard").
     * @param fallbackCategory Fallback category if recycling tag is unmapped or null.
     */
    fun mapDisposalAction(recyclingTag: String?, fallbackCategory: String): String {
        if (!recyclingTag.isNullOrBlank()) {
            val normalized = recyclingTag.substringAfter(':').trim().lowercase()
            val toks = normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
            fun has(vararg words: String): Boolean = words.any { it in toks }
            fun hasPrefix(prefix: String): Boolean = toks.any { it.startsWith(prefix) }
            val negated = has("not", "non", "no", "never", "dont", "don", "sans", "kein", "keine")
            val mentionsRecycle = hasPrefix("recycl") || hasPrefix("re-us") || has("reuse", "reutiliser", "recycler")
            // Negated recycle ("do-not-recycle", "non-recyclable", "not recyclable") → Discard.
            if (mentionsRecycle && negated) return DisposalAction.DISCARD
            if (has("compost") || hasPrefix("compost") || hasPrefix("biodegrad") || hasPrefix("compostable")) {
                return DisposalAction.COMPOST
            }
            if (mentionsRecycle) return DisposalAction.RECYCLE
            if (has("discard", "trash", "landfill", "bin", "poubelle", "mull") || hasPrefix("incinerat")) {
                return DisposalAction.DISCARD
            }
        }
        return when (fallbackCategory) {
            "Recyclable" -> DisposalAction.RECYCLE
            "Organic" -> DisposalAction.COMPOST
            else -> DisposalAction.DISCARD
        }
    }

    /**
     * Resolves all packaging components in the given Open Food Facts product.
     *
     * Iterates over [product.packagings], looking up material first (primary signal)
     * and falling back to shape if material is unmapped or null.
     * Weight is extracted from measured > declared > estimated (first valid).
     *
     * When only flat tag lists are present (no structured packagings), each list is
     * resolved INDEPENDENTLY — OFF does not guarantee `materialsTags[i] ↔ shapesTags[i]`
     * alignment, so positional pairing is deliberately NOT used. Material tags each become
     * a component; shape tags whose (category, subclass) is not already covered add their
     * own component. A recycling tag is applied to all components only when exactly ONE
     * is present (plausibly package-wide); otherwise disposal falls back to the category
     * default instead of smearing one tag across unrelated components.
     *
     * @param product The Open Food Facts product DTO.
     * @return List of resolved packaging components.
     */
    fun resolveComponents(product: OffProductDto): List<ResolvedPackagingComponent> {
        if (product.packagings.isNotEmpty()) {
            return product.packagings.map { component ->
                resolveComponent(component)
            }
        }

        // Fallback: independent resolution of each tag list (no positional pairing).
        val packageWideRecycling = product.recyclingTags.singleOrNull()
        val components = mutableListOf<ResolvedPackagingComponent>()
        val covered = mutableSetOf<Pair<String, String>>()
        for (matTag in product.materialsTags) {
            val (cat, sub) = getMaterialMapping(matTag) ?: continue
            val materialName = humanizeTag(matTag).takeIf { it.isNotBlank() } ?: sub
            components.add(
                ResolvedPackagingComponent(
                    shape = "Packaging",
                    material = materialName,
                    category = cat,
                    subclass = sub,
                    disposalAction = mapDisposalAction(packageWideRecycling, cat),
                    weightGrams = null
                )
            )
            covered.add(cat to sub)
        }
        for (shapeTag in product.shapesTags) {
            val (cat, sub) = getShapeFallback(shapeTag) ?: continue
            if ((cat to sub) in covered) continue
            val shapeName = humanizeTag(shapeTag).takeIf { it.isNotBlank() } ?: "Packaging"
            components.add(
                ResolvedPackagingComponent(
                    shape = shapeName,
                    material = sub,
                    category = cat,
                    subclass = sub,
                    disposalAction = mapDisposalAction(packageWideRecycling, cat),
                    weightGrams = null
                )
            )
            covered.add(cat to sub)
        }
        // Tags existed but nothing resolved: single honest unknown component so callers can
        // surface "packaging present, type unknown" instead of empty = "no packaging".
        if (components.isEmpty() && hasAnyTag(product)) {
            components.add(
                ResolvedPackagingComponent(
                    shape = "Packaging",
                    material = "Unknown",
                    category = "Trash",
                    subclass = "Miscellaneous Trash",
                    disposalAction = DisposalAction.DISCARD,
                    weightGrams = null
                )
            )
        }
        return components
    }

    private fun hasAnyTag(product: OffProductDto): Boolean =
        product.materialsTags.isNotEmpty() || product.shapesTags.isNotEmpty() ||
            product.recyclingTags.isNotEmpty()

    /**
     * Resolves the primary (most significant) packaging component for the given product.
     * Returns the heaviest component, or the first component if weights are equal or absent.
     *
     * @param product The Open Food Facts product DTO.
     * @return The primary [ResolvedPackagingComponent], or null if no components could be resolved.
     */
    fun resolvePrimaryComponent(product: OffProductDto): ResolvedPackagingComponent? {
        val components = resolveComponents(product)
        if (components.isEmpty()) return null
        return components.maxByOrNull { it.weightGrams ?: 0.0 }
    }

    /**
     * Resolves a single packaging component DTO into a domain [ResolvedPackagingComponent].
     *
     * Weight precedence: weight_measured > weight > weight_estimated (first VALID wins).
     * [ResolvedPackagingComponent.weightGrams] unit is **grams** (OFF convention).
     * Null policy: null = unknown (NOT zero) — unknown weights are excluded from the
     * heaviest-pick in [resolvePrimaryComponent] instead of fabricating mass. Non-finite,
     * negative and absurd (>[MAX_COMPONENT_WEIGHT_GRAMS], i.e. probable g/kg unit errors)
     * values all resolve to null.
     *
     * `lc_name` (localizedName) is provider-locale display text: trimmed and blank-guarded
     * here, but its LANGUAGE is whatever OFF served (may mix locales across components).
     * Category/subclass routing always uses the taxonomy `id`, never `lc_name`.
     */
    private fun resolveComponent(component: OffPackagingComponentDto): ResolvedPackagingComponent {
        val materialPair = component.material?.id?.let { getMaterialMapping(it) }
        val shapePair = component.shape?.id?.let { getShapeFallback(it) }

        val (category, subclass) = materialPair
            ?: shapePair
            ?: Pair("Trash", "Miscellaneous Trash")

        val shapeName = component.shape?.localizedName?.takeIf { it.isNotBlank() }
            ?: component.shape?.id?.let { humanizeTag(it) }?.takeIf { it.isNotBlank() }
            ?: "Packaging"

        val materialName = component.material?.localizedName?.takeIf { it.isNotBlank() }
            ?: component.material?.id?.let { humanizeTag(it) }?.takeIf { it.isNotBlank() }
            ?: subclass

        // Weight precedence: weight_measured > weight > weight_estimated (first VALID wins).
        val weight = listOfNotNull(
            component.weightMeasured,
            component.weight,
            component.weightEstimated
        ).firstOrNull { it.isFinite() && it >= 0.0 && it <= MAX_COMPONENT_WEIGHT_GRAMS }

        val disposal = mapDisposalAction(component.recycling?.id, category)

        return ResolvedPackagingComponent(
            shape = shapeName,
            material = materialName,
            category = category,
            subclass = subclass,
            disposalAction = disposal,
            weightGrams = weight
        )
    }
}
