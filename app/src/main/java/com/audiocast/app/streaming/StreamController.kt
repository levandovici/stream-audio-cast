package com.audiocast.app.streaming

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import com.audiocast.app.audio.MeteredMicrophoneSource
import com.audiocast.app.data.StreamConfig
import com.audiocast.app.video.StaticImageSource
import com.pedro.common.ConnectChecker
import com.pedro.encoder.CodecErrorCallback
import com.pedro.encoder.utils.CodecUtil.CodecTypeError
import com.pedro.library.rtmp.RtmpStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the whole streaming pipeline: static-image video source, metered
 * microphone source, H.264/AAC encoders and the RTMP client, all provided by
 * the RootEncoder library.
 *
 * Lifecycle:
 *   startStreaming(frame, config, key) -> CONNECTING -> LIVE
 *   updateBanner(frame)                -> live swap without reconnect
 *   stopStreaming()                    -> clean teardown -> DISCONNECTED
 *
 * Security invariant: the RTMP endpoint contains the stream key, so the
 * endpoint is never logged or placed into Status/error strings.
 */
class StreamController(
    context: Context,
    private val scope: CoroutineScope,
) {

    enum class State { DISCONNECTED, PREPARING, CONNECTING, LIVE, RECONNECTING, STOPPING, ERROR }

    data class Status(
        val state: State = State.DISCONNECTED,
        val error: String? = null,
        val liveSinceMs: Long = 0L,
        /** Smoothed output bitrate reported by the RTMP socket, in kbps. */
        val bitrateKbps: Int = 0,
        val sentVideoFrames: Long = 0,
        val droppedVideoFrames: Long = 0,
        val sentAudioFrames: Long = 0,
        val droppedAudioFrames: Long = 0,
        val congested: Boolean = false,
        val reconnectAttempt: Int = 0,
        val muted: Boolean = false,
    ) {
        val isLive: Boolean get() = state == State.LIVE
        val isBusy: Boolean get() =
            state == State.PREPARING || state == State.CONNECTING || state == State.STOPPING
        val isActive: Boolean get() =
            isLive || state == State.RECONNECTING || isBusy
    }

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val mutex = Mutex()

    private var stream: RtmpStream? = null
    private var imageSource: StaticImageSource? = null
    private var micSource: MeteredMicrophoneSource? = null
    private var config: StreamConfig? = null
    private var reconnectPolicy = ReconnectPolicy(8)

    /** Config the encoder was actually prepared with; null when not streaming. */
    var activeConfig: StreamConfig? = null
        private set

    private var userWantsLive = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var statsJob: Job? = null

    private val checker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            // The url contains the stream key: never log it, never store it in status.
            transition(State.CONNECTING)
        }

        override fun onConnectionSuccess() {
            reconnectAttempts = 0
            _status.update {
                it.copy(
                    state = State.LIVE,
                    error = null,
                    liveSinceMs = System.currentTimeMillis(),
                    reconnectAttempt = 0,
                )
            }
            startStats()
        }

        override fun onConnectionFailed(reason: String) {
            handleTransportFailure("Connection failed: $reason")
        }

        override fun onDisconnect() {
            // Fires on clean disconnect and on unexpected socket loss.
            if (userWantsLive && _status.value.state != State.STOPPING) {
                handleTransportFailure("Disconnected from ingest server")
            } else {
                transition(State.DISCONNECTED)
            }
        }

        override fun onAuthError() {
            failPermanently(
                "Twitch rejected the stream key. Check the key in Settings and try again."
            )
        }

        override fun onAuthSuccess() {}

        override fun onNewBitrate(bitrate: Long) {
            _status.update { it.copy(bitrateKbps = (bitrate / 1000).toInt()) }
        }
    }

    private val codecErrorCallback = object : CodecErrorCallback {
        override fun onCodecError(type: CodecTypeError, e: MediaCodec.CodecException) {
            handleTransportFailure("Encoder error (${type.name.lowercase()})")
        }

        override fun onEncodeError(type: CodecTypeError, e: IllegalStateException): Boolean {
            // Allow the library to reset the encoder once; a repeated failure
            // surfaces through the connection callbacks.
            return true
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Prepares encoders and starts the RTMP handshake. [frame] must be exactly
     * config.width x config.height. Takes ownership of [frame].
     */
    suspend fun startStreaming(
        frame: Bitmap,
        cfg: StreamConfig,
        streamKey: String,
    ) = mutex.withLock {
        if (_status.value.state != State.DISCONNECTED &&
            _status.value.state != State.ERROR
        ) return@withLock

        userWantsLive = true
        reconnectAttempts = 0
        reconnectPolicy = ReconnectPolicy(cfg.maxReconnectAttempts)
        config = cfg
        _status.update {
            Status(state = State.PREPARING, muted = it.muted)
        }

        val image = StaticImageSource(frame)
        val mic = MeteredMicrophoneSource { level -> _audioLevel.value = level }
        val rtmp = RtmpStream(appContext, checker, image, mic)
        val client = rtmp.getStreamClient()

        // RTMP logs would expose the endpoint (and the stream key). Keep off.
        client.setLogs(false)
        client.setSocketTimeout(cfg.socketTimeoutMs)
        // The library's internal retry allowance is left unbounded; the app's
        // own ReconnectPolicy decides when to give up.
        client.setReTries(Int.MAX_VALUE)
        // checkServerAlive falsely reports "No response from server" on some
        // ingests (RootEncoder issue #1924); socket errors still surface.
        client.setCheckServerAlive(false)
        client.shouldSendPings(true)
        rtmp.setEncoderErrorCallback(codecErrorCallback)

        try {
            val videoOk = rtmp.prepareVideo(
                cfg.width, cfg.height, cfg.videoBitrateBps, cfg.fps,
                /* iFrameInterval = */ 2, /* rotation = */ 0,
            )
            val audioOk = rtmp.prepareAudio(
                cfg.sampleRate, cfg.stereo, cfg.audioQuality.bitrateBps,
                /* echoCanceler = */ true, /* noiseSuppressor = */ true,
            )
            if (!videoOk || !audioOk) {
                image.release()
                failPermanently("The device encoder rejected the requested configuration")
                return@withLock
            }
        } catch (e: Exception) {
            image.release()
            failPermanently("Could not initialize encoders: ${e.message ?: e.javaClass.simpleName}")
            return@withLock
        }

        imageSource = image
        micSource = mic
        stream = rtmp
        activeConfig = cfg

        try {
            _status.update { it.copy(state = State.CONNECTING) }
            rtmp.startStream(TwitchIngest.buildEndpoint(cfg.ingestServer, streamKey))
        } catch (e: Exception) {
            failPermanently("Could not start the stream: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Replaces the frame fed to the encoder. Safe while streaming; takes
     * ownership of [newFrame]. No-ops when not streaming.
     */
    fun updateBanner(newFrame: Bitmap) {
        imageSource?.updateFrame(newFrame) ?: newFrame.recycle()
    }

    /** Whether the current stream accepts a live banner swap. */
    fun acceptsBannerUpdates(): Boolean = imageSource != null

    fun setMuted(muted: Boolean) {
        val mic = micSource ?: return
        if (muted) mic.mute() else mic.unMute()
        _status.update { it.copy(muted = muted) }
    }

    /**
     * Stops capture, encoders and the RTMP session, then releases resources.
     */
    suspend fun stopStreaming() = mutex.withLock {
        userWantsLive = false
        reconnectJob?.cancel()
        statsJob?.cancel()
        if (stream == null) {
            transition(State.DISCONNECTED)
            return@withLock
        }
        _status.update { it.copy(state = State.STOPPING) }
        runCatching { stream?.stopStream() }
        teardown()
        _audioLevel.value = 0f
        _status.value = Status(muted = _status.value.muted)
    }

    /** Full teardown when the app/service is destroyed. */
    suspend fun release() {
        stopStreaming()
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun handleTransportFailure(reason: String) {
        if (!userWantsLive) return
        if (reconnectPolicy.shouldRetry(reconnectAttempts + 1)) {
            reconnectAttempts++
            _status.update {
                it.copy(state = State.RECONNECTING, error = reason, reconnectAttempt = reconnectAttempts)
            }
            val attempt = reconnectAttempts
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(reconnectPolicy.delayFor(attempt))
                if (!userWantsLive) return@launch
                runCatching {
                    stream?.getStreamClient()?.reTry(0, "Reconnecting", null)
                }.onFailure {
                    handleTransportFailure("Reconnect failed")
                }
            }
        } else {
            failPermanently(
                "$reason — giving up after $reconnectAttempts attempt(s). Check the network and the stream key."
            )
        }
    }

    private fun failPermanently(message: String) {
        userWantsLive = false
        reconnectJob?.cancel()
        statsJob?.cancel()
        scope.launch {
            mutex.withLock {
                runCatching { stream?.stopStream() }
                teardown()
                _audioLevel.value = 0f
                _status.value = Status(state = State.ERROR, error = message, muted = _status.value.muted)
            }
        }
    }

    /** Stops sources and frees encoders/bitmaps. Caller must hold [mutex]. */
    private fun teardown() {
        imageSource = null
        micSource = null
        stream = null
        activeConfig = null
    }

    private fun transition(state: State) {
        _status.update { it.copy(state = state) }
    }

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                delay(1_000)
                val client = runCatching { stream?.getStreamClient() }.getOrNull() ?: break
                _status.update {
                    it.copy(
                        sentVideoFrames = client.getSentVideoFrames(),
                        droppedVideoFrames = client.getDroppedVideoFrames(),
                        sentAudioFrames = client.getSentAudioFrames(),
                        droppedAudioFrames = client.getDroppedAudioFrames(),
                        congested = client.hasCongestion(),
                    )
                }
            }
        }
    }
}
