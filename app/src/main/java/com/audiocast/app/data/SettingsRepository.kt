package com.audiocast.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.audiocast.app.streaming.TwitchIngest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audiocast_settings")

/**
 * Persists user preferences in DataStore. The stream key is NOT stored here —
 * it lives in [com.audiocast.app.security.SecureKeyStore].
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ORIENTATION = stringPreferencesKey("orientation")
        val FIT_MODE = stringPreferencesKey("fit_mode")
        val VIDEO_BITRATE_KBPS = intPreferencesKey("video_bitrate_kbps")
        val FPS = intPreferencesKey("fps")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val STEREO = booleanPreferencesKey("stereo")
        val INGEST_SERVER = stringPreferencesKey("ingest_server")
        val MAX_RECONNECT = intPreferencesKey("max_reconnect_attempts")
        val BANNER_URI = stringPreferencesKey("banner_uri")
    }

    val config: Flow<StreamConfig> = context.dataStore.data.map { prefs ->
        StreamConfig(
            orientation = prefs[Keys.ORIENTATION]
                ?.let { runCatching { StreamOrientation.valueOf(it) }.getOrNull() }
                ?: StreamOrientation.LANDSCAPE,
            fitMode = prefs[Keys.FIT_MODE]
                ?.let { runCatching { FitMode.valueOf(it) }.getOrNull() }
                ?: FitMode.FIT,
            videoBitrateBps = (prefs[Keys.VIDEO_BITRATE_KBPS] ?: 1_000) * 1_000,
            fps = prefs[Keys.FPS] ?: 30,
            audioQuality = prefs[Keys.AUDIO_QUALITY]
                ?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
                ?: AudioQuality.HIGH,
            stereo = prefs[Keys.STEREO] ?: false,
            ingestServer = prefs[Keys.INGEST_SERVER]
                ?.takeIf { TwitchIngest.isSupportedServer(it) }
                ?: TwitchIngest.DEFAULT_SERVER,
            maxReconnectAttempts = prefs[Keys.MAX_RECONNECT] ?: 8,
        )
    }

    val bannerUri: Flow<String?> = context.dataStore.data.map { it[Keys.BANNER_URI] }

    suspend fun setOrientation(value: StreamOrientation) {
        context.dataStore.edit { it[Keys.ORIENTATION] = value.name }
    }

    suspend fun setFitMode(value: FitMode) {
        context.dataStore.edit { it[Keys.FIT_MODE] = value.name }
    }

    suspend fun setVideoBitrateKbps(kbps: Int) {
        context.dataStore.edit { it[Keys.VIDEO_BITRATE_KBPS] = kbps.coerceIn(100, 8_000) }
    }

    suspend fun setFps(fps: Int) {
        context.dataStore.edit { it[Keys.FPS] = fps.coerceIn(15, 60) }
    }

    suspend fun setAudioQuality(value: AudioQuality) {
        context.dataStore.edit { it[Keys.AUDIO_QUALITY] = value.name }
    }

    suspend fun setStereo(value: Boolean) {
        context.dataStore.edit { it[Keys.STEREO] = value }
    }

    suspend fun setIngestServer(value: String) {
        context.dataStore.edit { it[Keys.INGEST_SERVER] = value.trim() }
    }

    suspend fun setMaxReconnectAttempts(value: Int) {
        context.dataStore.edit { it[Keys.MAX_RECONNECT] = value.coerceIn(0, 30) }
    }

    suspend fun setBannerUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.BANNER_URI) else it[Keys.BANNER_URI] = uri
        }
    }
}
