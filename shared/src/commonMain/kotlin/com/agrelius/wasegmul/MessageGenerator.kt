package com.agrelius.wasegmul

import kotlin.math.roundToInt
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
 *
 * ## i18n status (documented tech debt, not silently ignored)
 * All pools below are hard-coded English. They are intentionally kept as private,
 * enumerated template lists (one list per [ClassificationMode]) so extraction to
 * `strings.xml` / iOS `Localizable.strings` is mechanical: each lambda becomes one
 * format string with positional args (cat/sub/best/conf). Until that extraction lands,
 * non-English locales still receive English copy — tracked, not presented as translated.
 */
object MessageGenerator {

    // Injectable for tests; defaults to Random.Default for production.
    // Kept for backward compat — prefer the `random` parameter on generate()/generateBarcode().
    var rng: Random = Random.Default

    /**
     * Acronyms that must survive title-casing (key = lowercase input, value = display form).
     * Prevents "PET" → "Pet" / "HDPE" → "Hdpe" mangling in user-visible messages.
     */
    private val ACRONYMS = mapOf(
        "pet" to "PET", "rpet" to "rPET", "hdpe" to "HDPE", "ldpe" to "LDPE",
        "pvc" to "PVC", "pp" to "PP", "ps" to "PS", "eps" to "EPS",
        "pla" to "PLA", "pcb" to "PCB", "crt" to "CRT", "led" to "LED",
        "pe" to "PE", "abs" to "ABS"
    )

    /**
     * Humanises a model label for display. Preserves hyphenation (`Air-Conditioner` stays
     * hyphenated with both parts capitalised) and known acronyms ([ACRONYMS]).
     * NOTE: Kotlin common `lowercase()`/`uppercaseChar()` have no locale parameter; they
     * follow platform-default Unicode mappings (NOT Turkish-dotted-I safe). Machine-side
     * keys must use [WasteMapping.canonicalKey], never this display helper.
     */
    fun humanizeLabel(raw: String): String {
        val s = raw.trim().replace('_', ' ')
        if (s.isEmpty()) return s
        return s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
            word.split('-').filter { it.isNotEmpty() }.joinToString("-") { part ->
                ACRONYMS[part.lowercase()] ?: (part[0].uppercaseChar() + part.drop(1).lowercase())
            }
        }
    }

    /** NaN/infinite confidence renders as 0% (never throws, never claims certainty). */
    private fun pct(conf: Float): Int =
        if (!conf.isFinite()) 0 else ((conf.coerceIn(0f, 1f)) * 100f).roundToInt()

    // ── Case 1a: Both models agree, HIGH confidence (BOTH_AGREE_HIGH only) ──────
    // Strong wording is reserved for dual ≥0.80 consensus (see MLArbitrator
    // BOTH_AGREE_HIGH_THRESHOLD rationale); moderate agreement MUST use
    // agreeStandardConfidence below so 0.55/0.55 never claims "highly dependable".

    private val agreeHighConfidence = listOf(
        { cat: String, sub: String ->
            "Both our neural models agree: this is $sub ($cat). " +
                "You're looking at one of the clearest identifications our system can make."
        },
        { cat: String, sub: String ->
            "High-confidence match: category model says $cat, subclass model confirms $sub. " +
                "Dual-model consensus: this classification is reliable."
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

    // ── Case 1b: Both models agree, MODERATE confidence (BOTH_AGREE) ──────────
    // Deliberately hedged: no "high-confidence", "highly dependable" or "high
    // reliability" claims. A 0.55/0.55 agreement is a lead, not a verdict.

    private val agreeStandardConfidence = listOf(
        { cat: String, sub: String ->
            "Both models point to $sub ($cat), though neither is fully certain. " +
                "Treat this as a likely match and double-check before disposing."
        },
        { cat: String, sub: String ->
            "The category model suggests $cat and the subclass model leans toward $sub. " +
                "Moderate agreement: plausible, but verify against the item in hand."
        },
        { cat: String, sub: String ->
            "Preliminary consensus: $sub within the $cat family. " +
                "Confidence is moderate, so a quick manual check is recommended."
        },
        { cat: String, sub: String ->
            "Both classifiers lean the same way ($sub, $cat) without strong conviction. " +
                "Good enough for sorting guidance, not a guarantee."
        }
    )

    // ── Case 2: Category overrides: subclass has a match in top predictions ─

    private val categoryOverrideMatch = listOf(
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Our primary model is confident this belongs to $cat. " +
                "While the subclass model initially suggested $sub, we found $bestSub " +
                "(${pct(bestConf)}%) among its predictions, a better fit for the $cat category. " +
                "Using $bestSub as the final identification."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Category model override applied. The broad classifier identifies this as $cat with high confidence. " +
                "Cross-referencing subclass predictions: $bestSub at ${pct(bestConf)}% " +
                "aligns with the $cat classification. Final result: $bestSub."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Primary model says $cat. The subclass model's initial guess ($sub) doesn't match, " +
                "but $bestSub in its top predictions does. Selecting $bestSub for consistency with the $cat classification."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "Disagreement detected between models. Trusting the category classifier ($cat) and " +
                "finding the best subclass match from its prediction list: $bestSub at ${pct(bestConf)}%. " +
                "This $bestSub falls squarely within $cat."
        },
        { cat: String, sub: String, bestSub: String, bestConf: Float ->
            "The $cat classifier is more reliable at this confidence level. " +
                "Subclass model's top pick ($sub) was overridden; $bestSub " +
                "(${pct(bestConf)}%) matches the $cat grouping. Using $bestSub."
        }
    )

    // ── Case 3: Category overrides: no matching subclass in predictions ──────

    private val categoryOverrideNoMatch = listOf(
        { cat: String, sub: String ->
            "The primary classifier identifies this as $cat, but the subclass model " +
                "couldn't pinpoint a specific type. We're categorizing this as $cat: " +
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
                "Broad classification is still valid: treat this as $cat."
        },
        { cat: String, sub: String ->
            "Both models ran but disagree on specifics. The category classifier ($cat) " +
                "is more trustworthy here. Subclass model's output ($sub) doesn't map to $cat, " +
                "and no alternative match was found. Using $cat as the final result."
        }
    )

    // ── Case 4: Category-only (degraded: subclass model failed) ──────────────

    private val categoryOnlyDegraded = listOf(
        { cat: String ->
            "The fine-grained subclass model encountered an issue, but our primary " +
                "classifier still identified this as $cat. Result is category-level only: " +
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
                "The primary classifier says $cat. This is a coarser result than usual: " +
                "the specific material type couldn't be determined."
        }
    )

    // ── Case 5: Subclass-only (degraded: category model failed) ──────────────

    private val subclassOnlyDegraded = listOf(
        { sub: String, cat: String ->
            "The broad category model couldn't process this image, but our specialist " +
                "model identified it as $sub ($cat). Classification is based on the subclass " +
                "model alone - category was derived from the material mapping."
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
                "We're trusting the subclass result: it was able to identify the specific material type."
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
            "Low confidence across both classifiers: this image doesn't match our " +
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

    // Case 8: Barcode ground truth (high confidence barcode identification)
    private val barcodeGroundTruth = listOf(
        { product: String, sub: String, cat: String ->
            "Product identified via barcode: $product. " +
                "Packaging material: $sub ($cat). This is a verified identification " +
                "from the product database, providing near-certain classification."
        },
        { product: String, sub: String, cat: String ->
            "Barcode scan confirmed: $product. The packaging is $sub, classified as $cat. " +
                "Barcode-based identification bypasses visual ambiguity for deterministic results."
        },
        { product: String, sub: String, cat: String ->
            "Scanned barcode matches $product in our product database. " +
                "Packaging verified as $sub ($cat). This ground-truth identification " +
                "provides the highest reliability."
        },
        { product: String, sub: String, cat: String ->
            "Barcode lookup successful: $product. Material identified as $sub ($cat) " +
                "from manufacturer packaging data. No visual guesswork required."
        }
    )

    // Case 9: Barcode partial data
    private val barcodePartial = listOf(
        { product: String, sub: String, cat: String ->
            "Barcode identified this as $product, but packaging details are incomplete. " +
                "Best available classification: $sub ($cat). Confidence is moderate."
        },
        { product: String, sub: String, cat: String ->
            "Product matched via barcode: $product. Packaging data is partial, " +
                "so the classification of $sub ($cat) has been cross-validated with visual analysis."
        },
        { product: String, sub: String, cat: String ->
            "Barcode scan found $product with limited packaging metadata. " +
                "Classification: $sub ($cat), derived from available material tags."
        }
    )

    // Case 10: Barcode + visual consensus
    private val barcodeVisualConsensus = listOf(
        { product: String, sub: String, cat: String ->
            "Dual verification: barcode identifies $product, and our visual neural model " +
                "independently confirms $sub ($cat). Cross-modal agreement: highest confidence."
        },
        { product: String, sub: String, cat: String ->
            "Both barcode lookup ($product) and visual classification agree on $sub ($cat). " +
                "When barcode and camera models align, the result is exceptionally reliable."
        },
        { product: String, sub: String, cat: String ->
            "Barcode scan ($product) and neural vision analysis both point to $sub ($cat). " +
                "Multi-modal consensus achieved. Classification reliability: very high."
        }
    )

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * @param random source of variation; defaults to [rng] (kept for compat). Pass a seeded
     *   instance in tests for determinism.
     */
    fun generate(
        category: String,
        subcategory: String,
        catConfidence: Float,
        subConfidence: Float,
        topSubcategories: List<Pair<String, Float>>,
        topCategories: List<Pair<String, Float>>,
        mode: ClassificationMode,
        random: Random = rng
    ): String {
        val hSub = humanizeLabel(subcategory)
        val hCat = category.trim()
        return when (mode) {
            ClassificationMode.BOTH_AGREE_HIGH ->
                random.nextFrom(agreeHighConfidence)(hCat, hSub)
            ClassificationMode.BOTH_AGREE ->
                random.nextFrom(agreeStandardConfidence)(hCat, hSub)
            ClassificationMode.CATEGORY_OVERRIDE_MATCH -> {
                val best = findBestSubclassMatch(category, topSubcategories)
                if (best != null) {
                    val hBest = humanizeLabel(best.first)
                    random.nextFrom(categoryOverrideMatch)(hCat, hSub, hBest, best.second)
                } else {
                    random.nextFrom(categoryOverrideNoMatch)(hCat, hSub)
                }
            }
            ClassificationMode.CATEGORY_OVERRIDE_NO_MATCH ->
                random.nextFrom(categoryOverrideNoMatch)(hCat, hSub)
            ClassificationMode.CATEGORY_ONLY ->
                random.nextFrom(categoryOnlyDegraded)(hCat)
            ClassificationMode.SUBCLASS_ONLY -> {
                // Preserve the caller's category when it is a real taxonomy value instead
                // of blindly re-deriving from the mapping. Single match source with
                // MLArbitrator (which passes the already-resolved category); this only
                // changes behaviour for direct callers such as the iOS flat bridge.
                val callerCat = category.trim()
                val effectiveCat =
                    if (WasteMapping.isKnownCategory(callerCat) && !WasteMapping.isSentinel(callerCat)) {
                        callerCat
                    } else {
                        WasteMapping.getCategory(subcategory)
                    }
                random.nextFrom(subclassOnlyDegraded)(hSub, effectiveCat)
            }
            ClassificationMode.BOTH_UNCERTAIN ->
                random.nextFrom(bothUncertain)()
            ClassificationMode.UNMAPPED_SUBCLASS ->
                random.nextFrom(unmappedSubclass)(hSub)
            ClassificationMode.BARCODE_GROUND_TRUTH,
            ClassificationMode.BARCODE_PARTIAL,
            ClassificationMode.BARCODE_VISUAL_CONSENSUS ->
                // No product name is known at this layer: use a neutral determiner phrase.
                // Callers WITH a product name must use generateBarcode() (which requires one).
                barcodeMessage(productName = "this product", category = category, subclass = subcategory, mode = mode, random = random)
        }
    }

    /**
     * Barcode-grounded message. [productName] is REQUIRED (non-blank): the API never
     * invents names — a blank name throws [IllegalArgumentException] and the UI layer must
     * show its own localized "unknown product" fallback string instead.
     */
    fun generateBarcode(
        productName: String,
        category: String,
        subclass: String,
        mode: ClassificationMode,
        random: Random = rng
    ): String {
        require(productName.isNotBlank()) {
            "productName must be non-blank: MessageGenerator never synthesises product names; " +
                "the UI must render its own fallback for unresolved names."
        }
        return barcodeMessage(productName = productName, category = category, subclass = subclass, mode = mode, random = random)
    }

    private fun barcodeMessage(
        productName: String,
        category: String,
        subclass: String,
        mode: ClassificationMode,
        random: Random
    ): String {
        val hSub = humanizeLabel(subclass)
        val hCat = category.trim()
        return when (mode) {
            ClassificationMode.BARCODE_GROUND_TRUTH ->
                random.nextFrom(barcodeGroundTruth)(productName, hSub, hCat)
            ClassificationMode.BARCODE_PARTIAL ->
                random.nextFrom(barcodePartial)(productName, hSub, hCat)
            ClassificationMode.BARCODE_VISUAL_CONSENSUS ->
                random.nextFrom(barcodeVisualConsensus)(productName, hSub, hCat)
            else -> generate(
                category = category,
                subcategory = subclass,
                catConfidence = 0.95f,
                subConfidence = 0.95f,
                topSubcategories = emptyList(),
                topCategories = emptyList(),
                mode = mode,
                random = random
            )
        }
    }

    /**
     * Finds the best subclass prediction that maps to [targetCategory].
     * Returns null if no prediction matches — including when [targetCategory] itself is
     * blank or a sentinel ([WasteMapping.UNKNOWN]/[WasteMapping.UNCERTAIN]), so an UNKNOWN
     * target can never elect a garbage "best" match.
     */
    private fun findBestSubclassMatch(
        targetCategory: String,
        topSubcategories: List<Pair<String, Float>>
    ): Pair<String, Float>? {
        val trimmedTarget = targetCategory.trim()
        if (trimmedTarget.isBlank() || WasteMapping.isSentinel(trimmedTarget)) return null
        if (topSubcategories.isEmpty()) return null
        return topSubcategories
            .filter { WasteMapping.getCategory(it.first).equals(trimmedTarget, ignoreCase = true) }
            .maxByOrNull { it.second }
    }

    private fun <T> Random.nextFrom(list: List<T>): T {
        require(list.isNotEmpty()) { "message pool must not be empty" }
        return list.random(this)
    }
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
    UNMAPPED_SUBCLASS,
    /** Barcode identified product with complete packaging data (ground truth). */
    BARCODE_GROUND_TRUTH,
    /** Barcode identified product but packaging data is incomplete/legacy. */
    BARCODE_PARTIAL,
    /** Barcode and visual models agree on classification. */
    BARCODE_VISUAL_CONSENSUS
}
