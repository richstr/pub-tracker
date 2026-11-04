package com.richardstrang.pubtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val EFFECTIVE_PUB_COLUMNS = """
    p.id AS id, p.source AS source,
    COALESCE(o.name, p.name) AS name,
    COALESCE(o.street, p.street) AS street,
    COALESCE(o.lat, p.lat) AS lat,
    COALESCE(o.lon, p.lon) AS lon,
    COALESCE(o.category, p.category) AS category,
    COALESCE(o.status, p.status) AS status
"""

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    fun observe(id: String): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun get(id: String): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE deletedAt IS NULL")
    suspend fun getActive(): List<ChallengeEntity>

    @Insert
    suspend fun insert(challenge: ChallengeEntity)

    @Query("UPDATE challenges SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query(
        """
        SELECT cp.challengeId AS challengeId,
            COALESCE(o.status, p.status) AS status,
            cp.excluded AS excluded,
            EXISTS(SELECT 1 FROM visits v WHERE v.pubId = p.id AND v.deletedAt IS NULL) AS visited
        FROM challenge_pubs cp
        JOIN pubs p ON p.id = cp.pubId
        LEFT JOIN pub_overrides o ON o.pubId = p.id
        WHERE p.deletedAt IS NULL
        """
    )
    fun observeProgressRows(): Flow<List<ProgressRow>>
}

@Dao
interface ChallengePubDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ChallengePubEntity>)

    @Query("UPDATE challenge_pubs SET excluded = :excluded, updatedAt = :now WHERE challengeId = :challengeId AND pubId = :pubId")
    suspend fun setExcluded(challengeId: String, pubId: String, excluded: Boolean, now: Long)

    @Query("SELECT excluded FROM challenge_pubs WHERE challengeId = :challengeId AND pubId = :pubId")
    fun observeExcluded(challengeId: String, pubId: String): Flow<Boolean?>

    @Query(
        """
        SELECT p.id AS pubId, p.source AS source,
            COALESCE(o.name, p.name) AS name,
            COALESCE(o.street, p.street) AS street,
            COALESCE(o.lat, p.lat) AS lat,
            COALESCE(o.lon, p.lon) AS lon,
            COALESCE(o.category, p.category) AS category,
            COALESCE(o.status, p.status) AS status,
            cp.excluded AS excluded, cp.addedAt AS addedAt,
            (SELECT COUNT(*) FROM visits v WHERE v.pubId = p.id AND v.deletedAt IS NULL) AS visitCount
        FROM challenge_pubs cp
        JOIN pubs p ON p.id = cp.pubId
        LEFT JOIN pub_overrides o ON o.pubId = p.id
        WHERE cp.challengeId = :challengeId AND p.deletedAt IS NULL
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeForChallenge(challengeId: String): Flow<List<ChallengePubRow>>
}

@Dao
interface PubDao {
    @Query("SELECT * FROM pubs WHERE sourceRef IN (:refs)")
    suspend fun getBySourceRefs(refs: List<String>): List<PubEntity>

    @Query("SELECT * FROM pubs WHERE source = 'MANUAL' AND deletedAt IS NULL")
    suspend fun getManual(): List<PubEntity>

    @Query("SELECT * FROM pubs WHERE id = :id")
    suspend fun get(id: String): PubEntity?

    @Insert
    suspend fun insert(pub: PubEntity)

    @Insert
    suspend fun insertAll(pubs: List<PubEntity>)

    @Update
    suspend fun update(pub: PubEntity)

    @Update
    suspend fun updateAll(pubs: List<PubEntity>)

    @Query("SELECT * FROM pub_overrides WHERE pubId = :pubId")
    suspend fun getOverride(pubId: String): PubOverrideEntity?

    @Upsert
    suspend fun upsertOverride(override: PubOverrideEntity)

    @Query(
        """
        SELECT $EFFECTIVE_PUB_COLUMNS, p.houseNumber AS houseNumber, p.sourceTags AS sourceTags
        FROM pubs p LEFT JOIN pub_overrides o ON o.pubId = p.id
        WHERE p.id = :id
        """
    )
    fun observeDetail(id: String): Flow<PubDetailRow?>

    @Query(
        """
        SELECT $EFFECTIVE_PUB_COLUMNS, p.houseNumber AS houseNumber, p.sourceTags AS sourceTags
        FROM pubs p LEFT JOIN pub_overrides o ON o.pubId = p.id
        WHERE p.id = :id
        """
    )
    suspend fun getDetail(id: String): PubDetailRow?
}

@Dao
interface VisitDao {
    @Upsert
    suspend fun upsert(visit: VisitEntity)

    @Insert
    suspend fun insertAll(visits: List<VisitEntity>)

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun get(id: String): VisitEntity?

    @Transaction
    @Query("SELECT * FROM visits WHERE pubId = :pubId AND deletedAt IS NULL ORDER BY visitedAt IS NULL, visitedAt DESC")
    fun observeForPub(pubId: String): Flow<List<VisitWithPhotos>>

    @Query("UPDATE visits SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT DISTINCT pubId FROM visits WHERE deletedAt IS NULL AND pubId IN (:pubIds)")
    suspend fun visitedAmong(pubIds: List<String>): List<String>
}

@Dao
interface PhotoDao {
    @Insert
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("SELECT * FROM photos WHERE visitId = :visitId AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun forVisit(visitId: String): List<PhotoEntity>

    @Query("UPDATE photos SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)
}
