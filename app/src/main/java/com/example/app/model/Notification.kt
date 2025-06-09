package com.example.app.model

data class Notification(
    val event_id: String,
    val timestamp: String,
    val location: Location
) {
    data class Location(val latitude: Double, val longitude: Double)
}