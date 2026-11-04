package com.richstr.pubtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChallengeEntity::class,
        PubEntity::class,
        PubOverrideEntity::class,
        ChallengePubEntity::class,
        VisitEntity::class,
        VisitFtsEntity::class,
        PhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun challengeDao(): ChallengeDao
    abstract fun challengePubDao(): ChallengePubDao
    abstract fun pubDao(): PubDao
    abstract fun visitDao(): VisitDao
    abstract fun photoDao(): PhotoDao

    companion object {
        // No destructive fallback: schema changes must ship a migration so journals are never wiped.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pubtracker.db").build()
    }
}
