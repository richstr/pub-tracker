package com.richstr.pubtracker

import android.app.Application
import android.content.Context
import com.richstr.pubtracker.data.ChallengeRepository
import com.richstr.pubtracker.data.PhotoStore
import com.richstr.pubtracker.data.PubRepository
import com.richstr.pubtracker.data.db.AppDatabase
import com.richstr.pubtracker.data.osm.NominatimClient
import com.richstr.pubtracker.data.osm.OverpassClient
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import java.util.concurrent.TimeUnit

class PubTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
    }
}

class AppContainer(context: Context) {
    // OSM services require an identifying User-Agent.
    private val userAgent = "PubTracker/0.1 (Android; ${context.packageName})"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    private val database = AppDatabase.build(context)

    val photoStore = PhotoStore(context)
    val challengeRepository = ChallengeRepository(database, OverpassClient(http), NominatimClient(http))
    val pubRepository = PubRepository(database)
}
