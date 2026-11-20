package com.jupiterconnect.traffic

/** A city with fixed coordinates, used as the weather fallback. */
data class WeatherCity(val name: String, val lat: Double, val lon: Double)

/**
 * Only Hyderabad is kept as a fixed fallback location. Live weather prefers the
 * scooter's last-parked coordinates; this is used only when none are available.
 */
object Cities {
    val HYDERABAD = WeatherCity("Hyderabad", 17.3850, 78.4867)

    val list: List<WeatherCity> = listOf(HYDERABAD)

    fun byName(name: String?): WeatherCity = HYDERABAD
}
