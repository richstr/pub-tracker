package com.richardstrang.pubtracker.domain

import com.richardstrang.pubtracker.data.db.PubStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test
    fun closedAndExcludedPubsDoNotCountTowardsProgress() {
        val items = listOf(
            pubStateOf(PubStatus.OPEN, excluded = false, visited = true) to true,
            pubStateOf(PubStatus.OPEN, excluded = false, visited = false) to false,
            pubStateOf(PubStatus.OPEN, excluded = false, visited = false) to false,
            pubStateOf(PubStatus.CLOSED, excluded = false, visited = true) to true,
            pubStateOf(PubStatus.CLOSED, excluded = false, visited = false) to false,
            pubStateOf(PubStatus.OPEN, excluded = true, visited = true) to true,
        )
        val progress = Progress.of(items)
        assertEquals(1, progress.visited)
        assertEquals(2, progress.remaining)
        assertEquals(2, progress.closed)
        assertEquals(1, progress.closedVisited)
        assertEquals(3, progress.openTotal)
        assertEquals(33, progress.percent)
    }

    @Test
    fun emptyChallengeHasZeroProgress() {
        assertEquals(0f, Progress.of(emptyList()).fraction)
    }

    @Test
    fun distanceBetweenShrewsburyPubs() {
        // The Wheatsheaf, High Street to The Wheatsheaf, Frankwell: roughly 700 m apart.
        val d = Geo.distanceMetres(52.70686, -2.75256, 52.71096, -2.76002)
        assertTrue("was $d", d in 650.0..750.0)
    }

    @Test
    fun circlePointsSitOnTheRadius() {
        val ring = Geo.circle(52.7077, -2.754, 1500.0)
        assertEquals(ring.first(), ring.last())
        ring.forEach { (lat, lon) ->
            assertEquals(1500.0, Geo.distanceMetres(52.7077, -2.754, lat, lon), 1.0)
        }
    }
}
