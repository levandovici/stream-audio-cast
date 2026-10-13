package com.audiocast.app.image

import com.audiocast.app.data.FitMode
import kotlin.math.roundToInt

/** Integer rectangle in pixel space. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Result of mapping a source image into an output frame.
 *
 * - [src] is the region of the source image that is sampled.
 * - [dst] is the region of the output frame it is drawn into.
 * Area outside [dst] stays black (letterbox/pillarbox).
 */
data class ScaleResult(val src: IntRect, val dst: IntRect)

/**
 * Pure scale/crop math for mapping an arbitrary image into a fixed output
 * frame while preserving aspect ratio. All images are permitted; empty space
 * is black (FIT) or cropped away (FILL). Images are never stretched.
 */
object FrameGeometry {

    fun compute(srcW: Int, srcH: Int, dstW: Int, dstH: Int, mode: FitMode): ScaleResult {
        require(srcW > 0 && srcH > 0) { "Source must have positive size" }
        require(dstW > 0 && dstH > 0) { "Destination must have positive size" }

        return when (mode) {
            FitMode.FIT -> {
                // Largest source rect scaled to fit inside the frame.
                val scale = minOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
                val w = (srcW * scale).roundToInt().coerceIn(1, dstW)
                val h = (srcH * scale).roundToInt().coerceIn(1, dstH)
                ScaleResult(
                    src = IntRect(0, 0, srcW, srcH),
                    dst = IntRect(
                        (dstW - w) / 2,
                        (dstH - h) / 2,
                        (dstW - w) / 2 + w,
                        (dstH - h) / 2 + h,
                    ),
                )
            }

            FitMode.FILL -> {
                // Center-crop the source to the frame's aspect ratio, then draw
                // it across the whole frame.
                val frameAspect = dstW.toFloat() / dstH
                val srcAspect = srcW.toFloat() / srcH
                val cropW: Int
                val cropH: Int
                if (srcAspect > frameAspect) {
                    cropH = srcH
                    cropW = (srcH * frameAspect).roundToInt().coerceIn(1, srcW)
                } else {
                    cropW = srcW
                    cropH = (srcW / frameAspect).roundToInt().coerceIn(1, srcH)
                }
                val left = (srcW - cropW) / 2
                val top = (srcH - cropH) / 2
                ScaleResult(
                    src = IntRect(left, top, left + cropW, top + cropH),
                    dst = IntRect(0, 0, dstW, dstH),
                )
            }
        }
    }
}
