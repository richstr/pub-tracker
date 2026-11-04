package com.richstr.pubtracker.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** Closed ring of (lat, lon) points approximating a circle. */
    fun circle(lat: Double, lon: Double, radiusMetres: Double, points: Int = 64): List<Pair<Double, Double>> {
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val angular = radiusMetres / EARTH_RADIUS_M
        val ring = (0 until points).map { i ->
            val bearing = 2 * Math.PI * i / points
            val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
            val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
            Math.toDegrees(lat2) to Math.toDegrees(lon2)
        }
        return ring + ring.first()
    }

    /** Zoom level at which a circle of this radius fits a typical phone screen width. */
    fun zoomForRadius(radiusMetres: Int, lat: Double): Double {
        val metresPerDpAtZoom0 = 78_271.5 * cos(Math.toRadians(lat))
        val targetDiameterDp = 360.0 / 1.3
        return log2(metresPerDpAtZoom0 * targetDiameterDp / (2.0 * radiusMetres)).coerceIn(3.0, 18.0)
    }
}
