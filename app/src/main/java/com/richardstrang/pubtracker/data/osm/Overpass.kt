package com.richardstrang.pubtracker.data.osm

import com.richardstrang.pubtracker.data.db.PubCategory
import com.richardstrang.pubtracker.data.db.PubStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

data class OsmPlace(
    /** OSM element reference, e.g. "node/258056772". */
    val ref: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val street: String?,
    val houseNumber: String?,
    val category: PubCategory,
    val status: PubStatus,
    val tags: Map<String, String>,
)

object OverpassQuery {
    fun build(lat: Double, lon: Double, radiusMetres: Int, includePubs: Boolean, includeBars: Boolean): String {
        val around = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", radiusMetres, lat, lon)
        val kinds = buildList {
            if (includePubs) add("pub")
            if (includeBars) add("bar")
        }
        val clauses = kinds.joinToString("") { kind ->
            "nwr[\"amenity\"=\"$kind\"]$around;nwr[\"disused:amenity\"=\"$kind\"]$around;"
        }
        return "[out:json][timeout:60];($clauses);out center tags;"
    }
}

object OverpassParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<OsmPlace> =
        json.decodeFromString<OverpassResponse>(body).elements
            .mapNotNull { it.toPlace() }
            .distinctBy { it.ref }

    @Serializable
    private data class OverpassResponse(val elements: List<Element> = emptyList())

    @Serializable
    private data class Element(
        val type: String,
        val id: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val center: Center? = null,
        val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Center(val lat: Double, val lon: Double)

    private fun Element.toPlace(): OsmPlace? {
        val (category, status) = when {
            tags["amenity"] == "pub" -> PubCategory.PUB to PubStatus.OPEN
            tags["amenity"] == "bar" -> PubCategory.BAR to PubStatus.OPEN
            tags["disused:amenity"] == "pub" -> PubCategory.PUB to PubStatus.CLOSED
            tags["disused:amenity"] == "bar" -> PubCategory.BAR to PubStatus.CLOSED
            else -> return null
        }
        val placeLat = lat ?: center?.lat ?: return null
        val placeLon = lon ?: center?.lon ?: return null
        val name = tags["name"] ?: tags["disused:name"] ?: tags["old_name"]
            ?: if (category == PubCategory.BAR) "Unnamed bar" else "Unnamed pub"
        return OsmPlace(
            ref = "$type/$id",
            name = name,
            lat = placeLat,
            lon = placeLon,
            street = tags["addr:street"],
            houseNumber = tags["addr:housenumber"],
            category = category,
            status = status,
            tags = tags,
        )
    }
}

class OsmUnavailableException(cause: Throwable?) :
    IOException("OpenStreetMap servers are busy. Please try again in a minute.", cause)

/** Public Overpass servers are often at capacity, so try several with backoff. */
class OverpassClient(
    private val http: OkHttpClient,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {
    suspend fun fetchPlaces(
        lat: Double,
        lon: Double,
        radiusMetres: Int,
        includePubs: Boolean,
        includeBars: Boolean,
    ): List<OsmPlace> = withContext(Dispatchers.IO) {
        val query = OverpassQuery.build(lat, lon, radiusMetres, includePubs, includeBars)
        var lastError: Exception? = null
        repeat(ROUNDS) { round ->
            for (endpoint in endpoints) {
                try {
                    val body = post(endpoint, query)
                    if (body != null) return@withContext OverpassParser.parse(body)
                } catch (e: IOException) {
                    lastError = e
                } catch (e: SerializationException) {
                    lastError = e
                }
            }
            if (round < ROUNDS - 1) delay(BASE_DELAY_MS shl round)
        }
        throw OsmUnavailableException(lastError)
    }

    private fun post(endpoint: String, query: String): String? {
        val request = Request.Builder()
            .url(endpoint)
            .post(FormBody.Builder().add("data", query).build())
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string()
            // Overloaded servers can return a 200 with a plain-text error.
            return if (body.trimStart().startsWith("{")) body else null
        }
    }

    companion object {
        private const val ROUNDS = 3
        private const val BASE_DELAY_MS = 2_000L
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )
    }
}
