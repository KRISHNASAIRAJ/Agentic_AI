package com.jupiterconnect.traffic

/** A city with fixed coordinates, so weather works without a GPS fix. */
data class WeatherCity(val name: String, val lat: Double, val lon: Double)

/** Small built-in list of Indian cities for the free weather API. */
object Cities {
    val AUTO = WeatherCity("Auto (GPS)", 0.0, 0.0)

    val list: List<WeatherCity> = listOf(
        AUTO,
        WeatherCity("Bengaluru", 12.9716, 77.5946),
        WeatherCity("Delhi", 28.6139, 77.2090),
        WeatherCity("Mumbai", 19.0760, 72.8777),
        WeatherCity("Chennai", 13.0827, 80.2707),
        WeatherCity("Hyderabad", 17.3850, 78.4867),
        WeatherCity("Pune", 18.5204, 73.8567),
        WeatherCity("Kolkata", 22.5726, 88.3639),
        WeatherCity("Ahmedabad", 23.0225, 72.5714),
        WeatherCity("Jaipur", 26.9124, 75.7873),
        WeatherCity("Kochi", 9.9312, 76.2673),
        WeatherCity("Lucknow", 26.8467, 80.9462),
    )

    fun byName(name: String?): WeatherCity = list.firstOrNull {
        it.name.equals(name, ignoreCase = true)
    } ?: AUTO
}
