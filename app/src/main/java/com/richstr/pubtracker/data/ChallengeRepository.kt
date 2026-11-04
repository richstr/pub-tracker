package com.richstr.pubtracker.data

import androidx.room.withTransaction
import com.richstr.pubtracker.data.db.AppDatabase
import com.richstr.pubtracker.data.db.ChallengeEntity
import com.richstr.pubtracker.data.db.ChallengePubEntity
import com.richstr.pubtracker.data.db.ChallengePubRow
import com.richstr.pubtracker.data.db.PubCategory
import com.richstr.pubtracker.data.db.PubEntity
import com.richstr.pubtracker.data.db.PubSource
import com.richstr.pubtracker.data.db.PubStatus
import com.richstr.pubtracker.data.db.VisitEntity
import com.richstr.pubtracker.data.db.newId
import com.richstr.pubtracker.data.osm.NominatimClient
import com.richstr.pubtracker.data.osm.OsmPlace
import com.richstr.pubtracker.data.osm.OverpassClient
import com.richstr.pubtracker.data.osm.TownResult
import com.richstr.pubtracker.domain.Geo
import com.richstr.pubtracker.domain.Progress
import com.richstr.pubtracker.domain.pubStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

data class ChallengeSummary(val challenge: ChallengeEntity, val progress: Progress)

data class PreviewItem(
    val key: String,
    val name: String,
    val street: String?,
    val category: PubCategory,
    val status: PubStatus,
    val isManual: Boolean,
    val alreadyVisited: Boolean,
)

/** Search results plus the exact area searched, so the saved challenge always matches its pubs. */
data class ChallengePreview(
    val lat: Double,
    val lon: Double,
    val radiusMetres: Int,
    val includePubs: Boolean,
    val includeBars: Boolean,
    val places: List<OsmPlace>,
    val manualPubs: List<PubEntity>,
    val items: List<PreviewItem>,
)

private const val MANUAL_KEY_PREFIX = "manual:"

class ChallengeRepository(
    private val db: AppDatabase,
    private val overpass: OverpassClient,
    private val nominatim: NominatimClient,
) {
    private val challengeDao = db.challengeDao()
    private val challengePubDao = db.challengePubDao()
    private val pubDao = db.pubDao()
    private val visitDao = db.visitDao()

    fun observeSummaries(): Flow<List<ChallengeSummary>> =
        combine(challengeDao.observeAll(), challengeDao.observeProgressRows()) { challenges, rows ->
            val byChallenge = rows.groupBy { it.challengeId }
            challenges.map { challenge ->
                val items = byChallenge[challenge.id].orEmpty()
                    .map { pubStateOf(it.status, it.excluded, it.visited) to it.visited }
                ChallengeSummary(challenge, Progress.of(items))
            }
        }

    fun observeChallenge(id: String): Flow<ChallengeEntity?> = challengeDao.observe(id)

    fun observeChallengePubs(id: String): Flow<List<ChallengePubRow>> = challengePubDao.observeForChallenge(id)

    fun observeExcluded(challengeId: String, pubId: String): Flow<Boolean?> =
        challengePubDao.observeExcluded(challengeId, pubId)

    suspend fun getChallenge(id: String): ChallengeEntity? = challengeDao.get(id)

    suspend fun searchTowns(query: String): List<TownResult> = nominatim.search(query)

    suspend fun preview(
        lat: Double,
        lon: Double,
        radiusMetres: Int,
        includePubs: Boolean,
        includeBars: Boolean,
    ): ChallengePreview {
        val places = overpass.fetchPlaces(lat, lon, radiusMetres, includePubs, includeBars)
        val existing = getBySourceRefs(places.map { it.ref })
        val manual = pubDao.getManual().filter { pub ->
            Geo.distanceMetres(lat, lon, pub.lat, pub.lon) <= radiusMetres &&
                (if (pub.category == PubCategory.BAR) includeBars else includePubs)
        }
        val visited = visitDao.visitedAmong(existing.map { it.id } + manual.map { it.id }).toSet()
        val idByRef = existing.associate { it.sourceRef to it.id }

        val items = places.map { place ->
            PreviewItem(
                key = place.ref,
                name = place.name,
                street = place.street,
                category = place.category,
                status = place.status,
                isManual = false,
                alreadyVisited = idByRef[place.ref] in visited,
            )
        } + manual.map { pub ->
            PreviewItem(
                key = MANUAL_KEY_PREFIX + pub.id,
                name = pub.name,
                street = pub.street,
                category = pub.category,
                status = pub.status,
                isManual = true,
                alreadyVisited = pub.id in visited,
            )
        }
        return ChallengePreview(
            lat = lat,
            lon = lon,
            radiusMetres = radiusMetres,
            includePubs = includePubs,
            includeBars = includeBars,
            places = places,
            manualPubs = manual,
            items = items.sortedBy { it.name.lowercase() },
        )
    }

    suspend fun createChallenge(
        name: String,
        preview: ChallengePreview,
        alreadyVisitedKeys: Set<String>,
    ): String = db.withTransaction {
        val now = System.currentTimeMillis()
        val pubIdByKey = upsertOsmPlaces(preview.places, now) +
            preview.manualPubs.associate { (MANUAL_KEY_PREFIX + it.id) to it.id }

        val challenge = ChallengeEntity(
            id = newId(),
            name = name.trim(),
            centreLat = preview.lat,
            centreLon = preview.lon,
            radiusMetres = preview.radiusMetres,
            includePubs = preview.includePubs,
            includeBars = preview.includeBars,
            lastCheckedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        challengeDao.insert(challenge)
        challengePubDao.insertAll(
            pubIdByKey.values.distinct().map { pubId ->
                ChallengePubEntity(challenge.id, pubId, excluded = false, addedAt = now, createdAt = now, updatedAt = now)
            }
        )

        val tickedIds = alreadyVisitedKeys.mapNotNull { pubIdByKey[it] }
        val hasVisit = visitDao.visitedAmong(tickedIds).toSet()
        visitDao.insertAll(
            tickedIds.filterNot { it in hasVisit }.map { pubId ->
                VisitEntity(
                    id = newId(),
                    pubId = pubId,
                    visitedAt = null,
                    drinks = "",
                    rating = null,
                    notes = "",
                    createdAt = now,
                    updatedAt = now,
                )
            }
        )
        challenge.id
    }

    suspend fun setExcluded(challengeId: String, pubId: String, excluded: Boolean) =
        challengePubDao.setExcluded(challengeId, pubId, excluded, System.currentTimeMillis())

    suspend fun deleteChallenge(id: String) = challengeDao.softDelete(id, System.currentTimeMillis())

    /** Inserts new OSM places and refreshes known ones. Returns OSM ref -> pub id. */
    private suspend fun upsertOsmPlaces(places: List<OsmPlace>, now: Long): Map<String, String> {
        val existing = getBySourceRefs(places.map { it.ref }).associateBy { it.sourceRef }
        val toInsert = mutableListOf<PubEntity>()
        val toUpdate = mutableListOf<PubEntity>()
        for (place in places) {
            val tags = Json.encodeToString(place.tags)
            val known = existing[place.ref]
            if (known == null) {
                toInsert += PubEntity(
                    id = newId(),
                    source = PubSource.OSM,
                    sourceRef = place.ref,
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    street = place.street,
                    houseNumber = place.houseNumber,
                    category = place.category,
                    status = place.status,
                    sourceTags = tags,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                toUpdate += known.copy(
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    street = place.street,
                    houseNumber = place.houseNumber,
                    category = place.category,
                    status = place.status,
                    sourceTags = tags,
                    lastSeenAt = now,
                    updatedAt = now,
                )
            }
        }
        pubDao.insertAll(toInsert)
        pubDao.updateAll(toUpdate)
        return (toInsert + toUpdate).associate { it.sourceRef!! to it.id }
    }

    private suspend fun getBySourceRefs(refs: List<String>): List<PubEntity> =
        refs.chunked(500).flatMap { pubDao.getBySourceRefs(it) }
}
