package com.audiocast.app.audio

import android.media.MediaRecorder
import android.os.SystemClock
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.input.sources.audio.AudioSource
import kotlin.math.abs

/**
 * Microphone AudioSource that reports a throttled 0..1 peak level for the UI
 * meter while forwarding PCM unchanged to the encoder.
 *
 * Mirrors the upstream MicrophoneSource (which is final and cannot be
 * subclassed) with a metering tap added on the incoming PCM frames.
 */
class MeteredMicrophoneSource(
    private val audioSource: Int = MediaRecorder.AudioSource.DEFAULT,
    private val onLevel: (Float) -> Unit,
) : AudioSource(), GetMicrophoneData {

    private val microphone = MicrophoneManager(this)
    private var lastEmitMs = 0L

    override fun create(
        sampleRate: Int,
        isStereo: Boolean,
        echoCanceler: Boolean,
        noiseSuppressor: Boolean,
    ): Boolean {
        // Validate the parameters up-front, like the upstream source does.
        val result = microphone.createMicrophone(
            audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor
        )
        if (!result) throw IllegalArgumentException("Some parameters specified are not valid")
        return true
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (isRunning()) return
        val result = microphone.createMicrophone(
            audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor
        )
        if (!result) throw IllegalArgumentException("Failed to create microphone audio source")
        microphone.start()
    }

    override fun stop() {
        getMicrophoneData = null
        microphone.stop()
    }

    override fun isRunning(): Boolean = microphone.isRunning

    override fun release() {}

    override fun inputPCMData(frame: Frame) {
        emitLevel(frame)
        getMicrophoneData?.inputPCMData(frame)
    }

    private fun emitLevel(frame: Frame) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitMs < EMIT_INTERVAL_MS) return
        lastEmitMs = now
        onLevel(peakLevel(frame))
    }

    fun mute() = microphone.mute()

    fun unMute() = microphone.unMute()

    fun isMuted(): Boolean = microphone.isMuted

    companion object {
        private const val EMIT_INTERVAL_MS = 100L

        /** Peak level of a 16-bit little-endian PCM frame, normalized 0..1. */
        fun peakLevel(frame: Frame): Float {
            var peak = 0
            var i = frame.offset
            val end = frame.offset + frame.size
            while (i + 1 < end) {
                val sample = (frame.buffer[i + 1].toInt() shl 8) or
                    (frame.buffer[i].toInt() and 0xFF)
                val magnitude = abs(sample.toShort().toInt())
                if (magnitude > peak) peak = magnitude
                i += 2
            }
            return (peak / 32768f).coerceIn(0f, 1f)
        }
    }
}
