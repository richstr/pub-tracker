package com.richardstrang.pubtracker.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

enum class PubSource { OSM, MANUAL }
enum class PubCategory { PUB, BAR }
enum class PubStatus { OPEN, CLOSED }
enum class Visibility { PRIVATE, PUBLIC }

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val centreLat: Double,
    val centreLon: Double,
    val radiusMetres: Int,
    val includePubs: Boolean,
    val includeBars: Boolean,
    val lastCheckedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "pubs", indices = [Index(value = ["sourceRef"], unique = true)])
data class PubEntity(
    @PrimaryKey val id: String,
    val source: PubSource,
    /** e.g. "way/856905177" for OSM; null for manual pubs. */
    val sourceRef: String?,
    val name: String,
    val lat: Double,
    val lon: Double,
    val street: String?,
    val houseNumber: String?,
    val category: PubCategory,
    val status: PubStatus,
    /** Raw source tags as JSON. */
    val sourceTags: String?,
    val firstSeenAt: Long?,
    val lastSeenAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** User corrections to source data; null fields fall back to the pub's own values. */
@Entity(
    tableName = "pub_overrides",
    foreignKeys = [ForeignKey(PubEntity::class, ["id"], ["pubId"], onDelete = ForeignKey.CASCADE)],
)
data class PubOverrideEntity(
    @PrimaryKey val pubId: String,
    val name: String?,
    val street: String?,
    val category: PubCategory?,
    val status: PubStatus?,
    val lat: Double?,
    val lon: Double?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "challenge_pubs",
    primaryKeys = ["challengeId", "pubId"],
    indices = [Index("pubId")],
    foreignKeys = [
        ForeignKey(ChallengeEntity::class, ["id"], ["challengeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PubEntity::class, ["id"], ["pubId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChallengePubEntity(
    val challengeId: String,
    val pubId: String,
    val excluded: Boolean,
    val addedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "visits",
    indices = [Index("pubId")],
    foreignKeys = [ForeignKey(PubEntity::class, ["id"], ["pubId"], onDelete = ForeignKey.CASCADE)],
)
data class VisitEntity(
    @PrimaryKey val id: String,
    val pubId: String,
    /** Null when the visit date is unknown (e.g. ticked off when creating a challenge). */
    val visitedAt: Long?,
    val drinks: String,
    val rating: Int?,
    val notes: String,
    val visibility: Visibility = Visibility.PRIVATE,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Fts4(contentEntity = VisitEntity::class)
@Entity(tableName = "visits_fts")
data class VisitFtsEntity(
    val drinks: String,
    val notes: String,
)

@Entity(
    tableName = "photos",
    indices = [Index("visitId")],
    foreignKeys = [ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE)],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val visitId: String,
    val fileName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class VisitWithPhotos(
    @Embedded val visit: VisitEntity,
    @Relation(parentColumn = "id", entityColumn = "visitId")
    val photos: List<PhotoEntity>,
)

/** A pub within a challenge, with user overrides applied. */
data class ChallengePubRow(
    val pubId: String,
    val source: PubSource,
    val name: String,
    val street: String?,
    val lat: Double,
    val lon: Double,
    val category: PubCategory,
    val status: PubStatus,
    val excluded: Boolean,
    val addedAt: Long,
    val visitCount: Int,
)

data class ProgressRow(
    val challengeId: String,
    val status: PubStatus,
    val excluded: Boolean,
    val visited: Boolean,
)

/** A single pub with user overrides applied. */
data class PubDetailRow(
    val id: String,
    val source: PubSource,
    val name: String,
    val street: String?,
    val houseNumber: String?,
    val lat: Double,
    val lon: Double,
    val category: PubCategory,
    val status: PubStatus,
    val sourceTags: String?,
)
