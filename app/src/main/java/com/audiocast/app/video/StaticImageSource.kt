package com.audiocast.app.video

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.common.TimeUtils
import com.pedro.encoder.input.sources.video.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.time.Duration.Companion.nanoseconds

/**
 * VideoSource that continuously renders a pre-composed output frame (the
 * selected banner, already letterboxed/cropped to the exact encoder size)
 * into the streaming pipeline.
 *
 * The visual content is a static image - no camera, no screen capture.
 *
 * The frame can be swapped mid-stream via [updateFrame]; the swap happens at
 * a frame boundary so no partially rendered frame is ever emitted. This class
 * takes ownership of every bitmap handed to it and recycles replaced frames.
 */
class StaticImageSource(initialFrame: Bitmap) : VideoSource() {

    private val frameLock = Any()
    private var frame: Bitmap = initialFrame

    @Volatile
    private var running = false
    private var job: Job? = null
    private var surface: Surface? = null
    private val paint = Paint()

    /** Swap the displayed frame. Old frame is recycled. Safe while streaming. */
    fun updateFrame(newFrame: Bitmap) {
        synchronized(frameLock) {
            if (newFrame === frame) return
            val old = frame
            frame = newFrame
            if (!old.isRecycled) old.recycle()
        }
    }

    /** The current frame (do not recycle - owned by this source). */
    fun currentFrame(): Bitmap = synchronized(frameLock) { frame }

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        // Frame must match the encoder dimensions exactly.
        return synchronized(frameLock) {
            frame.width == width && frame.height == height && !frame.isRecycled
        }
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        if (isRunning()) return
        surfaceTexture.setDefaultBufferSize(width, height)
        surface = Surface(surfaceTexture)
        running = true
        job = CoroutineScope(Dispatchers.IO).launch {
            val frameInterval = 1_000_000_000L / fps
            var time = TimeUtils.getCurrentTimeNano()
            while (running) {
                try {
                    val canvas = surface?.lockCanvas(null)
                    if (canvas != null) {
                        synchronized(frameLock) {
                            if (!frame.isRecycled) canvas.drawBitmap(frame, 0f, 0f, paint)
                        }
                        surface?.unlockCanvasAndPost(canvas)
                    }
                } catch (_: Exception) {
                } finally {
                    // Pace output to the configured fps.
                    time += frameInterval
                    val now = TimeUtils.getCurrentTimeNano()
                    if (now - time > frameInterval) time = now
                    delay(max(time - now, 0).nanoseconds)
                }
            }
        }
    }

    override fun stop() {
        running = false
        runBlocking { job?.cancelAndJoin() }
        job = null
        surface?.release()
        surface = null
    }

    override fun release() {
        synchronized(frameLock) {
            if (!frame.isRecycled) frame.recycle()
        }
    }

    override fun isRunning(): Boolean = running
}
