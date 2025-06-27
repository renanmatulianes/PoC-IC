package com.example.app.model

data class Notification(
    val pedestrian_data: Pedestrian?,
    val driver_data: Driver?,
    val location: Location?,
    val driver_speed: Float,
    val timestamp: String
) {
    data class Location(
        val latitude: Double,
        val longitude: Double
    )

    data class Coordinates(
        val latitude:  Double,
        val longitude: Double,
        val speed:     Double? = null
    )

    data class Pedestrian(
        val risk_level: String,
        val object_direction: String   // "left" | "right" | "top" | "bottom"
    )
    data class Driver(
        val risk_level: String,
        val object_direction: String,
        val object_type: String,
        val object_coordinates: Coordinates
    )
}