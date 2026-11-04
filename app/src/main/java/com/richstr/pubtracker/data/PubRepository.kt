package com.richstr.pubtracker.data

import androidx.room.withTransaction
import com.richstr.pubtracker.data.db.AppDatabase
import com.richstr.pubtracker.data.db.ChallengePubEntity
import com.richstr.pubtracker.data.db.PhotoEntity
import com.richstr.pubtracker.data.db.PubCategory
import com.richstr.pubtracker.data.db.PubDetailRow
import com.richstr.pubtracker.data.db.PubEntity
import com.richstr.pubtracker.data.db.PubOverrideEntity
import com.richstr.pubtracker.data.db.PubSource
import com.richstr.pubtracker.data.db.PubStatus
import com.richstr.pubtracker.data.db.VisitEntity
import com.richstr.pubtracker.data.db.VisitWithPhotos
import com.richstr.pubtracker.data.db.newId
import com.richstr.pubtracker.domain.Geo
import kotlinx.coroutines.flow.Flow
import java.io.File

data class PubForm(
    val name: String,
    val street: String,
    val category: PubCategory,
    val closed: Boolean,
    val lat: Double,
    val lon: Double,
)

class PubRepository(private val db: AppDatabase) {
    private val pubDao = db.pubDao()
    private val visitDao = db.visitDao()
    private val photoDao = db.photoDao()
    private val challengeDao = db.challengeDao()
    private val challengePubDao = db.challengePubDao()

    fun observePub(id: String): Flow<PubDetailRow?> = pubDao.observeDetail(id)

    fun observeVisits(pubId: String): Flow<List<VisitWithPhotos>> = visitDao.observeForPub(pubId)

    suspend fun getPub(id: String): PubDetailRow? = pubDao.getDetail(id)

    suspend fun getVisit(id: String): Pair<VisitEntity, List<PhotoEntity>>? {
        val visit = visitDao.get(id) ?: return null
        return visit to photoDao.forVisit(id)
    }

    suspend fun saveVisit(
        visitId: String?,
        pubId: String,
        visitedAt: Long?,
        drinks: String,
        rating: Int?,
        notes: String,
        newPhotos: List<File>,
        removedPhotoIds: List<String>,
    ) = db.withTransaction {
        val now = System.currentTimeMillis()
        val existing = visitId?.let { visitDao.get(it) }
        val visit = existing?.copy(
            visitedAt = visitedAt,
            drinks = drinks.trim(),
            rating = rating,
            notes = notes.trim(),
            updatedAt = now,
        ) ?: VisitEntity(
            id = newId(),
            pubId = pubId,
            visitedAt = visitedAt,
            drinks = drinks.trim(),
            rating = rating,
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now,
        )
        visitDao.upsert(visit)
        photoDao.insertAll(newPhotos.map { PhotoEntity(newId(), visit.id, it.name, createdAt = now, updatedAt = now) })
        if (removedPhotoIds.isNotEmpty()) photoDao.softDelete(removedPhotoIds, now)
    }

    suspend fun deleteVisit(visitId: String) = visitDao.softDelete(visitId, System.currentTimeMillis())

    /** Creates a manual pub, or edits an existing one. OSM pubs get an override so refreshes don't undo edits. */
    suspend fun savePub(pubId: String?, challengeId: String?, form: PubForm): String = db.withTransaction {
        val now = System.currentTimeMillis()
        val status = if (form.closed) PubStatus.CLOSED else PubStatus.OPEN
        val street = form.street.trim().ifBlank { null }
        val existing = pubId?.let { pubDao.get(it) }

        when {
            existing == null -> {
                val pub = PubEntity(
                    id = newId(),
                    source = PubSource.MANUAL,
                    sourceRef = null,
                    name = form.name.trim(),
                    lat = form.lat,
                    lon = form.lon,
                    street = street,
                    houseNumber = null,
                    category = form.category,
                    status = status,
                    sourceTags = null,
                    firstSeenAt = null,
                    lastSeenAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
                pubDao.insert(pub)
                addToMatchingChallenges(pub, challengeId, now)
                pub.id
            }

            existing.source == PubSource.MANUAL -> {
                pubDao.update(
                    existing.copy(
                        name = form.name.trim(),
                        street = street,
                        category = form.category,
                        status = status,
                        lat = form.lat,
                        lon = form.lon,
                        updatedAt = now,
                    )
                )
                existing.id
            }

            else -> {
                val moved = Geo.distanceMetres(existing.lat, existing.lon, form.lat, form.lon) > 2.0
                pubDao.upsertOverride(
                    PubOverrideEntity(
                        pubId = existing.id,
                        name = form.name.trim().takeIf { it != existing.name },
                        street = street.takeIf { it != existing.street },
                        category = form.category.takeIf { it != existing.category },
                        status = status.takeIf { it != existing.status },
                        lat = form.lat.takeIf { moved },
                        lon = form.lon.takeIf { moved },
                        createdAt = pubDao.getOverride(existing.id)?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
                existing.id
            }
        }
    }

    private suspend fun addToMatchingChallenges(pub: PubEntity, challengeId: String?, now: Long) {
        val matching = challengeDao.getActive().filter { challenge ->
            val included = if (pub.category == PubCategory.BAR) challenge.includeBars else challenge.includePubs
            challenge.id == challengeId ||
                (included && Geo.distanceMetres(challenge.centreLat, challenge.centreLon, pub.lat, pub.lon) <= challenge.radiusMetres)
        }
        challengePubDao.insertAll(
            matching.map { ChallengePubEntity(it.id, pub.id, excluded = false, addedAt = now, createdAt = now, updatedAt = now) }
        )
    }
}
