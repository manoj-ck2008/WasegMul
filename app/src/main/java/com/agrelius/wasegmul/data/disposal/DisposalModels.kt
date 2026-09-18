package com.agrelius.wasegmul.data.disposal

enum class DisposalCenterType {
    DWCC, E_WASTE, COMPOST, WARD_OFFICE, RETAILER
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
)

data class NearbyCenterMatch(
    val center: DisposalCenter,
    val distanceKm: Double
)

data class CivicContact(
    val name: String,
    val number: String,
    val description: String,
    val isWhatsApp: Boolean = false
)
