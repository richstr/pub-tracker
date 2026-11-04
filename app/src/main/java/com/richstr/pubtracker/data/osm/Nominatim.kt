package com.richstr.pubtracker.data.osm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class TownResult(val name: String, val description: String, val lat: Double, val lon: Double)

/** OSM Nominatim geocoder. Usage policy: max 1 request/second and no search-as-you-type. */
class NominatimClient(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<TownResult> = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "5")
            .build()
        val results = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Town search failed (${response.code}).")
            json.decodeFromString<List<Result>>(response.body.string())
        }
        results.mapNotNull { result ->
            val lat = result.lat.toDoubleOrNull() ?: return@mapNotNull null
            val lon = result.lon.toDoubleOrNull() ?: return@mapNotNull null
            TownResult(
                name = result.name.ifBlank { result.displayName.substringBefore(",") },
                description = result.displayName,
                lat = lat,
                lon = lon,
            )
        }
    }

    @Serializable
    private data class Result(
        val name: String = "",
        @SerialName("display_name") val displayName: String = "",
        val lat: String,
        val lon: String,
    )
}
