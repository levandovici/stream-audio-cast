package com.audiocast.app.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max

/**
 * Renders the built-in branded banner used when the user has not picked an
 * image. Generated programmatically at exactly the encoder output size, so a
 * stream can start with zero assets selected.
 */
object DefaultBanner {

    private const val BG_CENTER = 0xFF241B3A.toInt()
    private const val BG_EDGE = 0xFF0F1117.toInt()
    private const val ACCENT = 0xFF9146FF.toInt()
    private const val TEXT = 0xFFECEEF2.toInt()
    private const val SUBTEXT = 0xFF9BA1AD.toInt()

    fun render(width: Int, height: Int): Bitmap {
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)

        // Dark background with a soft purple glow behind the title.
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f, height * 0.42f, max(width, height) * 0.75f,
                BG_CENTER, BG_EDGE, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        // Broadcast arcs above the title.
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            style = Paint.Style.STROKE
            strokeWidth = max(3f, width / 160f)
            strokeCap = Paint.Cap.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            style = Paint.Style.FILL
        }
        val cx = width / 2f
        val cy = height * 0.30f
        canvas.drawCircle(cx, cy, width / 60f, dotPaint)
        for (r in 1..3) {
            val radius = width / 60f + r * (width / 30f)
            canvas.drawArc(
                cx - radius, cy - radius, cx + radius, cy + radius,
                235f, 70f, false, arcPaint
            )
        }

        // Title, auto-fitted to ~86% of the frame width.
        val title = "Stream Audio Cast"
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        var size = width / 7f
        titlePaint.textSize = size
        while (titlePaint.measureText(title) > width * 0.86f && size > 8f) {
            size *= 0.92f
            titlePaint.textSize = size
        }
        val titleY = height * 0.47f
        canvas.drawText(title, cx, titleY, titlePaint)

        // Subtitle.
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SUBTEXT
            textSize = size * 0.32f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        canvas.drawText("LIVE AUDIO ON TWITCH", cx, titleY + size * 0.75f, subPaint)

        // Bottom accent bar.
        val bar = Paint().apply { color = ACCENT }
        canvas.drawRect(0f, height - height / 90f, width.toFloat(), height.toFloat(), bar)

        return frame
    }
}
