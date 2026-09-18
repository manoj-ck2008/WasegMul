package com.agrelius.wasegmul.data.disposal

enum class DisposalCenterType {
    DWCC, E_WASTE, COMPOST, WARD_OFFICE, RETAILER, HAZARDOUS
}

data class DisposalCenter(
    val id: String,
    val name: String,
    val type: DisposalCenterType,
    val zone: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val phone: String?,
    val operatingHours: String,
    val acceptedCategories: List<String>,
    val description: String = ""
) {
    init {
        // §3.40: lat/lon are required and range-checked at construction so a corrupt
        // asset row fails fast here (logged + skipped per-item) instead of producing
        // NaN distances downstream.
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be finite in -90..90 (was $latitude)"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be finite in -180..180 (was $longitude)"
        }
    }

    /**
     * Case-insensitive category match with normalization (§3.40): trims + compares
     * case-insensitively, and treats legacy `Trash` as [com.agrelius.wasegmul.data.WasteRecord.CATEGORY_RESIDUAL]
     * compat (asset rows predate the CONTEXT.md rename).
     */
    fun accepts(category: String): Boolean {
        val want = normalizeCategory(category)
        return acceptedCategories.any { normalizeCategory(it).equals(want, ignoreCase = true) }
    }

    companion object {
        fun normalizeCategory(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.equals("Trash", ignoreCase = true)) "Residual" else trimmed
        }
    }
}

data class NearbyCenterMatch(
    val center: DisposalCenter,
    val distanceKm: Double
)

data class CivicContact(
    val name: String,
    val number: String,
    val description: String,
    val isWhatsApp: Boolean = false
) {
    companion object {
        /**
         * i18n-ready phone guidance (§3.40): numbers SHOULD be stored/displayed in
         * E.164 (`+91…`) so dial/`tel:` intents work from any locale; short codes
         * (`1533`) are locale-bound and kept only for civic helplines with no E.164
         * equivalent. All numbers below are Bengaluru civic contacts — they must be
         * Remote-Config/region-aware before showing to non-Bengaluru users.
         */
        const val PHONE_POLICY_NOTE = "E.164 preferred; short codes are locale-bound civic helplines"
    }
}
