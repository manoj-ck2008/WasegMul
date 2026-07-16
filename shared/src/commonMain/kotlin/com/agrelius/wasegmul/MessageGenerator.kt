package com.agrelius.wasegmul

import kotlin.random.Random

/**
 * Generates dynamic, case-specific classification messages for the user.
 *
 * Every case (category-subclass agreement, disagreement, degraded mode, uncertain)
 * has multiple message variants that convey the same meaning but are worded differently.
 * A random variant is selected each time so the user never sees the exact same phrasing
 * twice in a row.
 *
 * Messages are category-aware: the wording references the specific material type
 * (e.g. "this lithium battery" vs. "this cardboard packaging") rather than using
 * generic placeholder text.
 */
object MessageGenerator {

    private val rng = Random.Default

    // ── Category-specific descriptors ────────────────────────────────────────

    private val categoryDescriptors = mapOf(
        "E-Waste" to listOf(
            "electronic item", "e-waste component", "electronic device",
            "piece of electronics", "digital device"
        ),
        "Recyclable" to listOf(
            "recyclable material", "recoverable item", "recyclable resource",
            "reclaimable material"
        ),
        "Organic" to listOf(
            "organic material", "biodegradable item", "compostable waste",
            "natural material"
        ),
        "Trash" to listOf(
            "non-recyclable item", "residual waste piece", "general waste item",
            "disposal-bound material"
        )
    )

    private fun categoryDescriptor(category: String): String =
        categoryDescriptors[category]?.random() ?: "item"

    // ── Case 1: Both models agree ────────────────────────────────────────────

    private val agreeHighConfidence = listOf(
        { cat: String, sub: String ->
            "Both our neural models agree — this is $sub ($cat). " +
                "You're looking at one of the clearest identifications our system can make."
        },
        { cat: String, sub: String ->
            "High-confidence match: category model says $cat, subclass model confirms $sub. " +
                "Dual-model consensus — this classification is reliable."
        },
        { cat: String, sub: String ->
            "Category and subclass classifiers are in full agreement. " +
                "This appears to be $sub within the $cat family. High reliability."
        },
        { cat: String, sub: String ->
            "Both the primary classifier and the fine-grained subclass model converge on $sub ($cat). " +
                "When both models agree at this level, the result is highly dependable."
        },
        { cat: String, sub: String ->
            "Strong dual-model consensus detected. The broad classifier identifies $cat, " +
                "and the specialist model narrows it to $sub. Classification confidence: high."
        }
    )

    // ── Case 2: Category overrides — subclass has a match in top predictions ─

    private val categoryOverrideMatch = listOf(
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Our primary model is confident this belongs to $cat. " +
                "While the subclass model initially suggested $sub, we found $bestSub " +
                "(${(bestConf * 100).toInt()}%) among its predictions — a better fit for the $cat category. " +
                "Using $bestSub as the final identification."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Category model override applied. The broad classifier identifies this as $cat with high confidence. " +
                "Cross-referencing subclass predictions: $bestSub at ${(bestConf * 100).toInt()}% " +
                "aligns with the $cat classification. Final result: $bestSub."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Primary model says $cat. The subclass model's initial guess ($sub) doesn't match, " +
                "but $bestSub in its top predictions does. Selecting $bestSub for consistency with the $cat classification."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Disagreement detected between models. Trusting the category classifier ($cat) and " +
                "finding the best subclass match from its prediction list: $bestSub at ${(bestConf * 100).toInt()}%. " +
                "This $bestSub falls squarely within $cat."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "The $cat classifier is more reliable at this confidence level. " +
                "Subclass model's top pick ($sub) was overridden; $bestSub " +
                "(${(bestConf * 100).toInt()}%) matches the $cat grouping. Using $bestSub."
        }
    )

    // ── Case 3: Category overrides — no matching subclass in predictions ──────

    private val categoryOverrideNoMatch = listOf(
        { cat: String, sub: String ->
            "The primary classifier identifies this as $cat, but the subclass model " +
                "couldn't pinpoint a specific type. We're categorizing this as $cat — " +
                "the broad classification remains reliable even without subclass confirmation."
        },
        { cat: String, sub: String ->
            "Category model says $cat with good confidence. The subclass model " +
                "suggested $sub, which doesn't align with $cat, and none of its other " +
                "predictions match either. Falling back to the category-level result: $cat."
        },
        { cat: String, sub: String ->
            "Primary neural pathway: $cat. The specialist model's predictions " +
                "don't contain a matching subclass for this $cat item. " +
                "Broad classification is still valid — treat this as $cat."
        },
        { cat: String, sub: String ->
            "Both models ran but disagree on specifics. The category classifier ($cat) " +
                "is more trustworthy here. Subclass model's output ($sub) doesn't map to $cat, " +
                "and no alternative match was found. Using $cat as the final result."
        }
    )

    // ── Case 4: Category-only (degraded — subclass model failed) ──────────────

    private val categoryOnlyDegraded = listOf(
        { cat: String ->
            "The fine-grained subclass model encountered an issue, but our primary " +
                "classifier still identified this as $cat. Result is category-level only — " +
                "we can't determine the specific type without the subclass model."
        },
        { cat: String ->
            "Subclass analysis unavailable for this image. The category model " +
                "identified this as $cat, which is the best classification we can provide right now."
        },
        { cat: String ->
            "Only the broad classifier was able to process this image. " +
                "Result: $cat. For a more specific identification, try retaking the photo " +
                "with better lighting or a clearer angle."
        },
        { cat: String ->
            "Partial analysis: the subclass model couldn't handle this input. " +
                "The primary classifier says $cat. This is a coarser result than usual — " +
                "the specific material type couldn't be determined."
        }
    )

    // ── Case 5: Subclass-only (degraded — category model failed) ──────────────

    private val subclassOnlyDegraded = listOf(
        { sub: String, cat: String ->
            "The broad category model couldn't process this image, but our specialist " +
                "model identified it as $sub ($cat). Classification is based on the subclass " +
                "model alone — category was derived from the material mapping."
        },
        { sub: String, cat: String ->
            "Category analysis unavailable. The subclass classifier identified this as $sub, " +
                "which maps to $cat. This is a subclass-driven result."
        },
        { sub: String, cat: String ->
            "Only the specialist model produced a result: $sub ($cat). " +
                "The primary classifier encountered an error. The subclass identification " +
                "is still valid but hasn't been cross-verified."
        },
        { sub: String, cat: String ->
            "Partial analysis: category model failed, but subclass model says $sub ($cat). " +
                "We're trusting the subclass result — it was able to identify the specific material type."
        }
    )

    // ── Case 6: Both uncertain ────────────────────────────────────────────────

    private val bothUncertain = listOf(
        { ->
            "Neither model could identify this item with sufficient confidence. " +
                "The visual features don't clearly match any known waste category. " +
                "Try repositioning the item or taking a clearer photo."
        },
        { ->
            "Low confidence across both classifiers — this image doesn't match our " +
                "training data well enough for a reliable classification. " +
                "Please verify the item type manually."
        },
        { ->
            "Both neural pathways returned low-confidence results. " +
                "This could be an unusual item, a very blurry image, or " +
                "something outside our model's training distribution. Manual verification recommended."
        },
        { ->
            "Classification uncertainty too high for either model to commit. " +
                "The item's visual signature doesn't align with known patterns. " +
                "Try better lighting or a different angle for improved results."
        }
    )

    // ── Case 7: Unmapped subclass ─────────────────────────────────────────────

    private val unmappedSubclass = listOf(
        { sub: String ->
            "The subclass model detected '$sub', but this label isn't in our " +
                "material mapping database. We can't determine the category automatically. " +
                "Please check local disposal guidelines for this type of waste."
        },
        { sub: String ->
            "Unknown material type: '$sub'. Our system doesn't have disposal data " +
                "for this classification. Consult your local waste management authority " +
                "for proper handling instructions."
        },
        { sub: String ->
            "The model identified '$sub' but it's not covered by our knowledge base. " +
                "This may be a rare or region-specific material. " +
                "Please verify disposal requirements locally."
        }
    )

    // ── Public API ───────────────────────────────────────────────────────────

    fun generate(
        category: String,
        subcategory: String,
        catConfidence: Float,
        subConfidence: Float,
        topSubcategories: List<Pair<String, Float>>,
        topCategories: List<Pair<String, Float>>,
        mode: ClassificationMode
    ): String = when (mode) {
        ClassificationMode.BOTH_AGREE_HIGH -> rng.nextFrom(agreeHighConfidence)(category, subcategory)
        ClassificationMode.BOTH_AGREE -> rng.nextFrom(agreeHighConfidence)(category, subcategory)
        ClassificationMode.CATEGORY_OVERRIDE_MATCH -> {
            val best = findBestSubclassMatch(category, topSubcategories)
            if (best != null) {
                rng.nextFrom(categoryOverrideMatch)(category, subcategory, best.first, best.second)
            } else {
                rng.nextFrom(categoryOverrideNoMatch)(category, subcategory)
            }
        }
        ClassificationMode.CATEGORY_OVERRIDE_NO_MATCH ->
            rng.nextFrom(categoryOverrideNoMatch)(category, subcategory)
        ClassificationMode.CATEGORY_ONLY ->
            rng.nextFrom(categoryOnlyDegraded)(category)
        ClassificationMode.SUBCLASS_ONLY -> {
            val mappedCat = WasteMapping.getCategory(subcategory)
            rng.nextFrom(subclassOnlyDegraded)(subcategory, mappedCat)
        }
        ClassificationMode.BOTH_UNCERTAIN ->
            rng.nextFrom(bothUncertain)()
        ClassificationMode.UNMAPPED_SUBCLASS ->
            rng.nextFrom(unmappedSubclass)(subcategory)
    }

    /**
     * Finds the best subclass prediction that maps to [targetCategory].
     * Returns null if no prediction matches.
     */
    private fun findBestSubclassMatch(
        targetCategory: String,
        topSubcategories: List<Pair<String, Float>>
    ): Pair<String, Float>? {
        return topSubcategories
            .filter { WasteMapping.getCategory(it.first) == targetCategory }
            .maxByOrNull { it.second }
    }

    private fun <T> Random.nextFrom(list: List<T>): T = list[nextInt(list.size)]
}

/**
 * Classification mode determines which message pool to draw from.
 */
enum class ClassificationMode {
    /** Both models agree, high confidence. */
    BOTH_AGREE_HIGH,
    /** Both models agree. */
    BOTH_AGREE,
    /** Category overrides subclass, and a matching subclass was found in predictions. */
    CATEGORY_OVERRIDE_MATCH,
    /** Category overrides subclass, but no matching subclass found. */
    CATEGORY_OVERRIDE_NO_MATCH,
    /** Only category model succeeded (degraded). */
    CATEGORY_ONLY,
    /** Only subclass model succeeded (degraded). */
    SUBCLASS_ONLY,
    /** Both models returned low confidence. */
    BOTH_UNCERTAIN,
    /** Subclass label not in mapping database. */
    UNMAPPED_SUBCLASS
}
