package com.example.app

import android.location.Location
import com.example.app.model.Notification
import com.example.app.model.PsmNotification
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Duration
import java.time.Instant

class NotificationProcessor(private val callback: NotificationActionCallback) {

    interface NotificationActionCallback {
        fun onShowNotification(notification: Notification, ttc: Double?)
    }

    private var mostRecentNotification: Notification? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val psmAdapter = moshi.adapter(PsmNotification::class.java)

    fun processMessage(jsonText: String) {
        val psmNotif = psmAdapter.fromJson(jsonText) ?: return
        val newNotif = psm2notif(psmNotif)

        if (!isNotificationMoreImportant(mostRecentNotification, newNotif)) {
            //return
        }

        mostRecentNotification = newNotif
        val ttc = timeToCollision(newNotif)

        callback.onShowNotification(newNotif, ttc)
    }

    // Todas as funções de lógica de negócio foram movidas para cá
    private fun psm2notif(psm: PsmNotification): Notification {
        val location = Notification.Location(
            latitude = psm.position.latitude,
            longitude = psm.position.longitude
        )
        val coords = Notification.Coordinates(
            latitude = psm.position.latitude,
            longitude = psm.position.longitude,
            speed = psm.speed * 0.02
        )
        val driverData = Notification.Driver(
            risk_level = "high",
            object_direction = "left",
            object_type = "HUMAN",
            object_coordinates = coords
        )
        return Notification(
            driver_data = driverData,
            location = location,
            driver_speed = psm.speed.toFloat(),
            timestamp = Instant.now().toString()
        )
    }

    private fun isNotificationMoreImportant(oldN: Notification?, newN: Notification): Boolean {
        if (oldN == null) return true
        val ttcOld = timeToCollision(oldN)
        val ttcNew = timeToCollision(newN)
        val tsOld = Instant.parse(oldN.timestamp)
        val tsNew = Instant.parse(newN.timestamp)
        val elapsedSec = Duration.between(tsOld, tsNew).toMillis().coerceAtLeast(0L) / 1000.0
        val remOld = ttcOld?.minus(elapsedSec)
        return when {
            remOld != null && remOld <= 0.0 -> true
            remOld == null && ttcNew != null -> true
            remOld != null && ttcNew == null -> false
            remOld != null && ttcNew != null -> ttcNew < remOld
            else -> false
        }
    }

    private fun timeToCollision(notification: Notification?): Double? {
        val userLocation = notification?.location ?: return null
        val driverData = notification.driver_data ?: return null
        val objectCoords = driverData.object_coordinates
        val objectSpeed = objectCoords.speed ?: return null
        val userSpeed = notification.driver_speed.toDouble()
        val direction = driverData.object_direction.lowercase()
        val dist = distanceMeters(
            userLocation.latitude, userLocation.longitude,
            objectCoords.latitude, objectCoords.longitude
        )
        val vRel = when (direction) {
            "front", "left", "right" -> userSpeed + objectSpeed
            "rear" -> if (objectSpeed > userSpeed) objectSpeed - userSpeed else -1.0
            else -> -1.0
        }
        return if (vRel > 0.0) dist / vRel else null
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }
}