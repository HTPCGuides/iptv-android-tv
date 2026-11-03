package com.iptvtv.data

import com.iptvtv.model.DEFAULT_ACE_ENGINE
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.iptvtv.model.ACE_KEEP_CHOICES
import com.iptvtv.model.DEFAULT_PREBUFFER_MS
import com.iptvtv.model.PREBUFFER_CHOICES
import com.iptvtv.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences in Preferences DataStore, read as one [Settings] flow. */
class SettingsDataSource(private val store: DataStore<Preferences>) {
    private object Keys {
        val hideGeo = booleanPreferencesKey("hide_geo_blocked")
        val subtitles = booleanPreferencesKey("subtitles_on")
        val prebuffer = intPreferencesKey("prebuffer_ms")
        val aceKeep = intPreferencesKey("ace_keep_versions")
        val aceEngine = stringPreferencesKey("ace_engine_addr")
        /** Set once the SharedPreferences-era data has been carried over. */
        val migrated = booleanPreferencesKey("legacy_migrated")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            hideGeoBlocked = p[Keys.hideGeo] ?: false,
            subtitlesOn = p[Keys.subtitles] ?: false,
            // Anything unknown (an old build's value, a hand-edited store) reads as the default.
            prebufferMs = p[Keys.prebuffer]?.takeIf { it in PREBUFFER_CHOICES } ?: DEFAULT_PREBUFFER_MS,
            aceKeepVersions = p[Keys.aceKeep]?.takeIf { it in ACE_KEEP_CHOICES } ?: 0,
            aceEngineAddr = p[Keys.aceEngine]?.takeIf { it.isNotBlank() } ?: DEFAULT_ACE_ENGINE,
        )
    }

    val legacyMigrated: Flow<Boolean> = store.data.map { it[Keys.migrated] ?: false }

    suspend fun setHideGeoBlocked(on: Boolean) { store.edit { it[Keys.hideGeo] = on } }
    suspend fun setSubtitlesOn(on: Boolean) { store.edit { it[Keys.subtitles] = on } }
    suspend fun setPrebufferMs(ms: Int) { store.edit { it[Keys.prebuffer] = ms } }
    suspend fun setAceKeepVersions(n: Int) { store.edit { it[Keys.aceKeep] = n } }
    suspend fun setAceEngineAddr(addr: String) { store.edit { it[Keys.aceEngine] = addr } }
    suspend fun markLegacyMigrated() { store.edit { it[Keys.migrated] = true } }
}
