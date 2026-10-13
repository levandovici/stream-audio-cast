package com.audiocast.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audiocast.app.AudioCastApp
import com.audiocast.app.data.AudioQuality
import com.audiocast.app.data.FitMode
import com.audiocast.app.data.StreamConfig
import com.audiocast.app.data.StreamOrientation
import com.audiocast.app.image.BannerDecoder
import com.audiocast.app.image.DefaultBanner
import com.audiocast.app.image.FrameComposer
import com.audiocast.app.service.StreamService
import com.audiocast.app.streaming.StreamController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI snapshot for the currently selected banner. */
data class BannerState(
    val uri: Uri,
    /** Decoded source image (bounded to a sane resolution). */
    val source: Bitmap,
    /** Exact frame fed to the encoder - what Twitch viewers will see. */
    val previewFrame: Bitmap,
    val displayName: String? = null,
)

/**
 * Single shared ViewModel for the three screens. Holds the selected banner,
 * the persisted stream configuration and mirrors the StreamController state.
 */
class AudioCastViewModel(application: Application) : AndroidViewModel(application) {

    private val app: AudioCastApp get() = getApplication()
    private val controller: StreamController get() = app.streamController
    private val settings get() = app.settingsRepository
    private val keyStore get() = app.secureKeyStore

    val status: StateFlow<StreamController.Status> = controller.status
    val audioLevel: StateFlow<Float> = controller.audioLevel

    val config: StateFlow<StreamConfig> = settings.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamConfig())

    private val _banner = MutableStateFlow<BannerState?>(null)
    val banner: StateFlow<BannerState?> = _banner.asStateFlow()

    /** Branded default frame shown/streamed when no image is picked. */
    private val _defaultFrame = MutableStateFlow<Bitmap?>(null)
    val defaultFrame: StateFlow<Bitmap?> = _defaultFrame.asStateFlow()

    private val _hasSavedKey = MutableStateFlow(false)
    val hasSavedKey: StateFlow<Boolean> = _hasSavedKey.asStateFlow()

    /** One-shot error/info messages surfaced via snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        _hasSavedKey.value = keyStore.hasStreamKey()
        // Keep a branded default frame ready for the current orientation.
        viewModelScope.launch {
            config.collect { cfg ->
                val old = _defaultFrame.value
                _defaultFrame.value = withContext(Dispatchers.Default) {
                    DefaultBanner.render(cfg.width, cfg.height)
                }
                if (old != null && !old.isRecycled) old.recycle()
            }
        }
        // Restore the previously selected banner while permission is held.
        viewModelScope.launch {
            settings.bannerUri.stateIn(viewModelScope).collect { stored ->
                if (_banner.value == null && stored != null) loadBanner(Uri.parse(stored), persist = false)
            }
        }
    }

    // ------------------------------------------------------------------
    // Stream key
    // ------------------------------------------------------------------

    fun saveStreamKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            _message.value = "Enter a stream key first"
            return
        }
        keyStore.saveStreamKey(trimmed)
        _hasSavedKey.value = true
        _message.value = "Stream key saved securely"
    }

    fun clearStreamKey() {
        keyStore.clearStreamKey()
        _hasSavedKey.value = false
        _message.value = "Saved stream key cleared"
    }

    // ------------------------------------------------------------------
    // Banner selection
    // ------------------------------------------------------------------

    /**
     * Decodes + validates a picked image and makes it the current banner.
     * Safe to call while streaming - the outgoing frame is swapped live.
     */
    fun onBannerPicked(uri: Uri) {
        viewModelScope.launch {
            // Persist read access so the banner survives app restarts.
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            loadBanner(uri, persist = true)
        }
    }

    private suspend fun loadBanner(uri: Uri, persist: Boolean) {
        val result = withContext(Dispatchers.IO) {
            BannerDecoder.decode(app.contentResolver, uri)
        }
        when (result) {
            is BannerDecoder.DecodeResult.Failure -> {
                _message.value = result.reason
                if (persist) settings.setBannerUri(null)
            }
            is BannerDecoder.DecodeResult.Success -> {
                applyBanner(uri, result.bitmap, persist)
            }
        }
    }

    private suspend fun applyBanner(uri: Uri, source: Bitmap, persist: Boolean) {
        val cfg = config.value
        val frame = withContext(Dispatchers.Default) {
            FrameComposer.compose(source, cfg.width, cfg.height, cfg.fitMode)
        }
        val old = _banner.value
        _banner.value = BannerState(uri, source, frame, displayNameOf(uri))
        if (old != null && old.source !== source && !old.source.isRecycled) {
            old.source.recycle()
        }
        if (persist) settings.setBannerUri(uri.toString())

        // If a stream is live, swap the outgoing frame at a frame boundary.
        // The live frame must match the ACTIVE encoder dimensions, which can
        // differ from the stored config if settings changed mid-stream.
        val active = controller.activeConfig
        if (controller.status.value.isActive && controller.acceptsBannerUpdates() && active != null) {
            val liveFrame = withContext(Dispatchers.Default) {
                FrameComposer.compose(source, active.width, active.height, active.fitMode)
            }
            controller.updateBanner(liveFrame)
        }
    }

    /** Removes the selected banner. Falls back to the branded default frame,
     * including live while streaming. */
    fun clearBanner() {
        val old = _banner.value
        _banner.value = null
        viewModelScope.launch {
            settings.setBannerUri(null)
            old?.let {
                if (!it.source.isRecycled) it.source.recycle()
                if (!it.previewFrame.isRecycled) it.previewFrame.recycle()
            }
            // If live, swap the outgoing frame to the default banner now.
            val active = controller.activeConfig
            if (old != null && controller.status.value.isActive &&
                controller.acceptsBannerUpdates() && active != null) {
                val frame = withContext(Dispatchers.Default) {
                    DefaultBanner.render(active.width, active.height)
                }
                controller.updateBanner(frame)
            }
            if (old != null) _message.value = "Cover removed — branded banner will be used"
        }
    }

    /** Recompose the output frame after orientation/fit-mode changes. */
    private suspend fun recomposeFrame() {
        val b = _banner.value ?: return
        val cfg = config.value
        val frame = withContext(Dispatchers.Default) {
            FrameComposer.compose(b.source, cfg.width, cfg.height, cfg.fitMode)
        }
        _banner.update { it?.copy(previewFrame = frame) }
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    // ------------------------------------------------------------------
    // Stream lifecycle
    // ------------------------------------------------------------------

    /**
     * Starts the stream. RECORD_AUDIO must already be granted (checked in the
     * UI layer so a rationale can be shown).
     */
    fun startStream() {
        val key = keyStore.getStreamKey()
        if (key.isNullOrEmpty()) {
            _message.value = "Save your Twitch stream key first"
            return
        }
        val cfg = config.value
        viewModelScope.launch {
            // Fresh frame for the encoder; the source owns it from here.
            // Falls back to the branded default banner when nothing is picked.
            val b = _banner.value
            val frame = withContext(Dispatchers.Default) {
                if (b != null) FrameComposer.compose(b.source, cfg.width, cfg.height, cfg.fitMode)
                else DefaultBanner.render(cfg.width, cfg.height)
            }
            ContextCompat.startForegroundService(
                app, StreamService.startIntent(app)
            )
            controller.startStreaming(frame, cfg, key)
        }
    }

    fun stopStream() {
        viewModelScope.launch {
            controller.stopStreaming()
        }
    }

    fun setMuted(muted: Boolean) = controller.setMuted(muted)

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    fun setOrientation(value: StreamOrientation) {
        viewModelScope.launch {
            settings.setOrientation(value)
            recomposeFrame()
        }
    }

    fun setFitMode(value: FitMode) {
        viewModelScope.launch {
            settings.setFitMode(value)
            recomposeFrame()
        }
    }

    fun setVideoBitrateKbps(kbps: Int) {
        viewModelScope.launch { settings.setVideoBitrateKbps(kbps) }
    }

    fun setFps(fps: Int) {
        viewModelScope.launch { settings.setFps(fps) }
    }

    fun setAudioQuality(value: AudioQuality) {
        viewModelScope.launch { settings.setAudioQuality(value) }
    }

    fun setStereo(value: Boolean) {
        viewModelScope.launch { settings.setStereo(value) }
    }

    fun setIngestServer(value: String) {
        viewModelScope.launch { settings.setIngestServer(value) }
    }

    fun setMaxReconnectAttempts(value: Int) {
        viewModelScope.launch { settings.setMaxReconnectAttempts(value) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
