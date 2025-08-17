package com.example.app

import android.location.Location
import com.example.app.model.Notification
import java.time.Duration
import java.time.Instant

object NotificationUtils {

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }

    fun timeToCollision(notification: Notification?): Double? {
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
            "front", "left", "right" -> {
                userSpeed + objectSpeed
            }
            "rear" -> {
                if (objectSpeed > userSpeed) {
                    objectSpeed - userSpeed
                } else {
                    -1.0
                }
            }
            else -> -1.0
        }

        return if (vRel > 0.0) dist / vRel else null
    }

    fun isNotificationMoreImportant(oldN: Notification?, newN: Notification): Boolean {
        if (oldN == null) return true

        val ttcOld = timeToCollision(oldN)
        val ttcNew = timeToCollision(newN)

        // Se a notificação antiga e a nova são para o mesmo objeto, não precisamos recalcular o TTC
        // Esta é uma otimização, mas a lógica original funciona para todos os casos.
        if (oldN.driver_data?.object_coordinates == newN.driver_data?.object_coordinates && ttcNew != null && ttcOld != null) {
            val tsOld = Instant.parse(oldN.timestamp)
            val tsNew = Instant.parse(newN.timestamp)
            val elapsedMs = Duration.between(tsOld, tsNew).toMillis().coerceAtLeast(0L)
            val elapsedSec = elapsedMs / 1000.0
            val remOld = ttcOld.minus(elapsedSec)
            return ttcNew < remOld
        }

        // Lógica original para comparar notificações de objetos diferentes ou quando uma não tem TTC
        return when {
            ttcOld == null && ttcNew != null -> true
            ttcOld != null && ttcNew == null -> false
            ttcOld != null && ttcNew != null -> ttcNew < ttcOld
            else -> true // Se ambos forem nulos, processe a mais nova
        }
    }
}