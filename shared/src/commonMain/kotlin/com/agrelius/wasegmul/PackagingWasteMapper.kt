package com.agrelius.wasegmul

import com.agrelius.wasegmul.network.OffPackagingComponentDto
import com.agrelius.wasegmul.network.OffProductDto
import kotlinx.serialization.Serializable

/**
 * Resolved packaging component with human-readable descriptions,
 * WasegMul domain Category and Subclass, disposal instructions,
 * and optional weight in grams.
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
 */
object PackagingWasteMapper {

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
        "en:bioplastic" to Pair("Organic", "Organic"),
        "en:pla" to Pair("Organic", "Organic"),
        "en:biodegradable-plastic" to Pair("Organic", "Organic"),

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

        // Composites & Multilayer
        "en:tetra-pak" to Pair("Recyclable", "Cardboard"),
        "en:tetrapak" to Pair("Recyclable", "Cardboard"),
        "en:composite-material" to Pair("Trash", "Miscellaneous Trash"),
        "en:composite" to Pair("Trash", "Miscellaneous Trash"),
        "en:c-pap" to Pair("Trash", "Miscellaneous Trash"),
        "en:multilayer" to Pair("Trash", "Plastic"),

        // Natural & Organic Materials
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
     */
    val SHAPE_FALLBACK: Map<String, Pair<String, String>> = mapOf(
        "en:bottle" to Pair("Recyclable", "Plastic"),
        "en:can" to Pair("Recyclable", "Metal"),
        "en:box" to Pair("Recyclable", "Cardboard"),
        "en:jar" to Pair("Recyclable", "Glass"),
        "en:carton" to Pair("Recyclable", "Cardboard"),
        "en:tub" to Pair("Recyclable", "Plastic"),
        "en:pot" to Pair("Recyclable", "Plastic"),
        "en:tray" to Pair("Recyclable", "Plastic"),
        "en:bag" to Pair("Recyclable", "Plastic"),
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
        "en:aerosol" to Pair("Recyclable", "Metal"),
        "en:tin" to Pair("Recyclable", "Metal"),
        "en:envelope" to Pair("Recyclable", "Paper"),
        "en:sheet" to Pair("Recyclable", "Paper"),
        "en:clamshell" to Pair("Recyclable", "Plastic"),
        "en:blister-pack" to Pair("Trash", "Miscellaneous Trash"),
        "en:tube" to Pair("Trash", "Plastic"),
        "en:punnet" to Pair("Recyclable", "Plastic"),
        "en:barrel" to Pair("Recyclable", "Metal"),
        "en:drum" to Pair("Recyclable", "Metal"),
        "en:keg" to Pair("Recyclable", "Metal"),
        "en:flagon" to Pair("Recyclable", "Glass"),
        "en:vial" to Pair("Recyclable", "Glass"),
        "en:ampoule" to Pair("Recyclable", "Glass")
    )

    /**
     * Finds the Category and Subclass mapping for an OFF material tag ID,
     * supporting tags with or without the language prefix.
     */
    fun getMaterialMapping(tag: String?): Pair<String, String>? {
        if (tag.isNullOrBlank()) return null
        val clean = tag.trim().lowercase()
        val withEn = if (clean.contains(':')) clean else "en:$clean"
        val withoutEn = clean.substringAfter(':')
        return MATERIAL_MAPPING[withEn]
            ?: MATERIAL_MAPPING[clean]
            ?: MATERIAL_MAPPING[withoutEn]
    }

    /**
     * Finds the fallback Category and Subclass mapping for an OFF shape tag ID,
     * supporting tags with or without the language prefix.
     */
    fun getShapeFallback(tag: String?): Pair<String, String>? {
        if (tag.isNullOrBlank()) return null
        val clean = tag.trim().lowercase()
        val withEn = if (clean.contains(':')) clean else "en:$clean"
        val withoutEn = clean.substringAfter(':')
        return SHAPE_FALLBACK[withEn]
            ?: SHAPE_FALLBACK[clean]
            ?: SHAPE_FALLBACK[withoutEn]
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
     * "Recycle", "Discard", or "Compost".
     *
     * @param recyclingTag Recycling taxonomy ID (e.g. "en:recycle", "en:discard").
     * @param fallbackCategory Fallback category if recycling tag is unmapped or null.
     */
    fun mapDisposalAction(recyclingTag: String?, fallbackCategory: String): String {
        if (recyclingTag.isNullOrBlank()) {
            return when (fallbackCategory) {
                "Recyclable" -> "Recycle"
                "Organic" -> "Compost"
                else -> "Discard"
            }
        }
        val normalized = recyclingTag.substringAfter(':').trim().lowercase()
        return when {
            normalized.contains("recycle") || normalized.contains("reuse") || normalized.contains("re-use") -> "Recycle"
            normalized.contains("discard") || normalized.contains("trash") || normalized.contains("bin") || normalized.contains("incinerat") -> "Discard"
            normalized.contains("compost") || normalized.contains("biodegrad") -> "Compost"
            else -> when (fallbackCategory) {
                "Recyclable" -> "Recycle"
                "Organic" -> "Compost"
                else -> "Discard"
            }
        }
    }

    /**
     * Resolves all packaging components in the given Open Food Facts product.
     *
     * Iterates over [product.packagings], looking up material first (primary signal)
     * and falling back to shape if material is unmapped or null.
     * Weight is extracted from measured > declared > estimated (first non-null).
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

        // Fallback: If structured packagings list is empty, synthesize from tag lists if present
        val materials = product.materialsTags
        val shapes = product.shapesTags
        val maxCount = maxOf(materials.size, shapes.size)
        if (maxCount > 0) {
            return (0 until maxCount).map { index ->
                val matTag = materials.getOrNull(index)
                val shapeTag = shapes.getOrNull(index)
                val recTag = product.recyclingTags.getOrNull(index) ?: product.recyclingTags.firstOrNull()

                val materialPair = matTag?.let { getMaterialMapping(it) }
                val shapePair = shapeTag?.let { getShapeFallback(it) }
                val (cat, sub) = materialPair ?: shapePair ?: Pair("Trash", "Miscellaneous Trash")

                val shapeName = shapeTag?.let { humanizeTag(it) }?.takeIf { it.isNotBlank() } ?: "Packaging"
                val materialName = matTag?.let { humanizeTag(it) }?.takeIf { it.isNotBlank() } ?: sub
                val disposal = mapDisposalAction(recTag, cat)

                ResolvedPackagingComponent(
                    shape = shapeName,
                    material = materialName,
                    category = cat,
                    subclass = sub,
                    disposalAction = disposal,
                    weightGrams = null
                )
            }
        }

        return emptyList()
    }

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

        // Weight precedence: weight_measured > weight > weight_estimated
        val weight = component.weightMeasured
            ?: component.weight
            ?: component.weightEstimated

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
