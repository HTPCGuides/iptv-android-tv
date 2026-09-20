package com.iptvtv

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.iptvtv.data.AceEngine
import com.iptvtv.data.HttpPlaylistFetcher
import com.iptvtv.data.IptvRepository
import com.iptvtv.data.LegacyMigration
import com.iptvtv.data.OfflineFirstIptvRepository
import com.iptvtv.data.PlaybackSession
import com.iptvtv.data.SettingsDataSource
import com.iptvtv.data.db.IptvDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Hand-wired dependencies. One repository, one playback session, one database: a DI
 * framework would be more code than the graph it wires.
 */
class AppContainer(app: Application) {
    private val db = Room.databaseBuilder(app, IptvDatabase::class.java, "iptv.db").build()
    private val settings = SettingsDataSource(app.settingsStore)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: IptvRepository
    val playback = PlaybackSession()
    val aceEngine = AceEngine(app)

    /** Completes once the built-ins are seeded and any pre-Room data is carried over; screens wait on it. */
    val ready: Job

    init {
        val repo = OfflineFirstIptvRepository(
            dao = db.dao(),
            fetcher = HttpPlaylistFetcher(),
            settingsStore = settings,
            cacheDirs = listOf(app.cacheDir),
        )
        repository = repo
        ready = scope.launch {
            repo.seedBuiltIns()
            LegacyMigration(app, db.dao(), settings).runIfNeeded()
        }
    }
}

class IptvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
