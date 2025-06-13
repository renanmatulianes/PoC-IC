package com.example.app.model

data class Notification(
    val pedestrian_data: Pedestrian?,
    val driver_data: Driver?
) {
    data class Pedestrian(
        val risk_level: String,
        val object_direction: String   // "left" | "right" | "top" | "bottom"
    )
    data class Driver(
        val risk_level: String,
        val object_direction: String
    )
}