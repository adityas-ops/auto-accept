package com.rideassist

/** Extraction of fare/distances/type from the text of a ride-request card. */
data class RideCard(
    val fare: Double?,
    val pickupKm: Double?,
    val tripKm: Double?,
    val type: String?,
    val isBoost: Boolean = false,
    val text: String,
)

object RideParser {
    // Matches: ₹28, ₹ 1,250.50, Rs 50, INR 100
    private val fareRe = Regex("""(?:₹|Rs\.?|INR)\s*([0-9]+(?:,[0-9]+)*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    // Markers specific to Rapido and modern driver cards:
    // Pickup distance is preceded by a bullet: "• 2.1 Km" or "• 500 m"
    private val pickupBulletKmRe = Regex("""(?:[•·]|\bpickup\b)\s*(\d+(?:\.\d+)?)\s*(?:km|kms)\b""", RegexOption.IGNORE_CASE)
    private val pickupBulletMRe = Regex("""(?:[•·]|\bpickup\b)\s*(\d+)\s*m\b""", RegexOption.IGNORE_CASE)

    // Drop distance is preceded by an arrow or drop keyword: "▼ 6.4 Km" or "drop 5 km"
    private val dropArrowKmRe = Regex("""(?:[▼▾v↓→]|\bdrop\b)\s*(\d+(?:\.\d+)?)\s*(?:km|kms)\b""", RegexOption.IGNORE_CASE)

    // Generic fallback distance regexes
    private val genericKmRe = Regex("""(\d+(?:\.\d+)?)\s*(?:km|kms)\b""", RegexOption.IGNORE_CASE)
    private val genericMRe = Regex("""(\d+)\s*m\b""")

    private val typeKeywords = linkedMapOf(
        "parcel" to listOf("parcel", "delivery", "courier"),
        "auto" to listOf("auto"),
        "cab" to listOf("cab", "sedan", "suv", "prime", "mini"),
        "bike" to listOf("bike", "moto", "taxi"),
    )

    fun parse(texts: List<String>): RideCard {
        val joined = texts.joinToString(" | ")

        // Parse fare (handles commas like 1,250)
        val rawFare = fareRe.find(joined)?.groupValues?.get(1)?.replace(",", "")
        val fare = rawFare?.toDoubleOrNull()

        // Check for Boost / Saver discounts
        val lower = joined.lowercase()
        val isBoost = lower.contains("boost") || lower.contains("saver")

        // Try structured marker extraction first
        var pickup = pickupBulletKmRe.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
        if (pickup == null) {
            pickup = pickupBulletMRe.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()?.div(1000)
        }

        var trip = dropArrowKmRe.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()

        // If markers not found, fallback to sequential order
        val allKms = genericKmRe.findAll(joined).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        if (pickup == null) {
            pickup = allKms.getOrNull(0) ?: genericMRe.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()?.div(1000)
            if (trip == null) {
                trip = allKms.getOrNull(1)
            }
        } else if (trip == null) {
            val remaining = allKms.filter { it != pickup }
            trip = remaining.firstOrNull() ?: allKms.getOrNull(1)
        }

        val type = typeKeywords.entries.firstOrNull { (_, ks) -> ks.any { lower.contains(it) } }?.key

        return RideCard(fare, pickup, trip, type, isBoost, joined)
    }
}
