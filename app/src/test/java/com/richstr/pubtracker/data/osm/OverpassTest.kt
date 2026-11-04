package com.richstr.pubtracker.data.osm

import com.richstr.pubtracker.data.db.PubCategory
import com.richstr.pubtracker.data.db.PubStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassTest {
    private val places: List<OsmPlace> by lazy {
        val body = javaClass.classLoader!!.getResource("overpass_shrewsbury.json")!!.readText()
        OverpassParser.parse(body)
    }

    @Test
    fun parsesShrewsburySample() {
        assertEquals(75, places.size)
        assertEquals(21, places.count { it.category == PubCategory.BAR })
        assertEquals(4, places.count { it.status == PubStatus.CLOSED })
        assertTrue(places.all { it.name.isNotBlank() })
    }

    @Test
    fun keepsSameNamedPubsSeparate() {
        val wheatsheafs = places.filter { it.name == "The Wheatsheaf" }
        assertEquals(2, wheatsheafs.size)
        assertNotEquals(wheatsheafs[0].ref, wheatsheafs[1].ref)
        assertEquals(setOf("High Street", "Frankwell"), wheatsheafs.map { it.street }.toSet())
    }

    @Test
    fun usesCentreForBuildingsAndWays() {
        val frankwell = places.single { it.ref == "way/856905177" }
        assertEquals(52.71096, frankwell.lat, 0.0001)
        assertEquals(-2.76002, frankwell.lon, 0.0001)
    }

    @Test
    fun ignoresElementsThatAreNotPubsOrBars() {
        val body = """{"elements":[
            {"type":"node","id":1,"lat":1.0,"lon":2.0,"tags":{"amenity":"cafe","name":"Cafe"}},
            {"type":"node","id":2,"lat":1.0,"lon":2.0,"tags":{"amenity":"pub"}},
            {"type":"node","id":3,"tags":{"amenity":"pub","name":"No location"}}
        ]}"""
        val parsed = OverpassParser.parse(body)
        assertEquals(listOf("node/2"), parsed.map { it.ref })
        assertEquals("Unnamed pub", parsed.single().name)
    }

    @Test
    fun queryOnlyIncludesSelectedCategories() {
        val pubsOnly = OverpassQuery.build(52.7077, -2.754, 1500, includePubs = true, includeBars = false)
        assertTrue("\"amenity\"=\"pub\"" in pubsOnly)
        assertTrue("\"disused:amenity\"=\"pub\"" in pubsOnly)
        assertFalse("bar" in pubsOnly)
        assertTrue("(around:1500,52.707700,-2.754000)" in pubsOnly)

        val both = OverpassQuery.build(52.7077, -2.754, 1500, includePubs = true, includeBars = true)
        assertTrue("\"amenity\"=\"bar\"" in both)
    }
}
