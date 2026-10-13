package com.audiocast.app.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.audiocast.app.data.FitMode
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Decodes, validates and frames banner images chosen through the Storage
 * Access Framework. Never uploads or persists the image.
 */
object BannerDecoder {

    /** Decoded source images are downsampled beyond this edge for safety. */
    const val MAX_DECODE_DIMENSION = 4096

    sealed class DecodeResult {
        data class Success(val bitmap: Bitmap) : DecodeResult()
        data class Failure(val reason: String) : DecodeResult()
    }

    /** Any image/… type is accepted; the decoder validates the actual bytes. */
    fun isSupportedMimeType(mime: String?): Boolean =
        mime == null || mime.lowercase().startsWith("image/")

    fun mimeTypeFor(context: Context, uri: Uri): String? =
        context.contentResolver.getType(uri)

    /**
     * Decodes the image at [uri] with subsampling so neither dimension exceeds
     * [maxDimension]. Returns a descriptive [DecodeResult.Failure] for
     * unsupported, corrupt or inaccessible inputs.
     */
    fun decode(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = MAX_DECODE_DIMENSION,
    ): DecodeResult {
        val mime = resolver.getType(uri)
        if (mime != null && !isSupportedMimeType(mime)) {
            return DecodeResult.Failure("Unsupported file type: $mime")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            val opened = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds); true
            } ?: false
            if (!opened) {
                return DecodeResult.Failure(
                    "The image provider did not allow reading this file " +
                        "(mime=${mime ?: "unknown"}). Try a different file or folder."
                )
            }
        } catch (e: SecurityException) {
            return DecodeResult.Failure(
                "Read permission was denied for this file. Pick it again or " +
                    "choose a file from local storage."
            )
        } catch (e: Exception) {
            return DecodeResult.Failure(
                "Could not open the selected image (${e.javaClass.simpleName}: ${e.message})"
            )
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return DecodeResult.Failure("The selected file is not a valid image")
        }

        val sample = computeSampleSize(
            bounds.outWidth, bounds.outHeight, maxDimension
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            val raw = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (raw == null) return DecodeResult.Failure("The image could not be decoded")
            // Photos often store orientation only in EXIF; honor it so a
            // portrait shot is not streamed sideways.
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            DecodeResult.Success(applyExifOrientation(raw, orientation))
        } catch (e: OutOfMemoryError) {
            DecodeResult.Failure("The image is too large for this device")
        } catch (e: Exception) {
            DecodeResult.Failure(
                "The image could not be decoded (${e.javaClass.simpleName}: ${e.message})"
            )
        }
    }

    /** Rotates/mirrors [bitmap] according to the EXIF orientation flag. */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setRotate(180f).also { matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.setRotate(90f).also { matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.setRotate(-90f).also { matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Testable overload that works on a raw stream. */
    fun decodeStream(
        stream: InputStream,
        maxDimension: Int = MAX_DECODE_DIMENSION,
    ): DecodeResult {
        val bytes = try {
            stream.readBytes()
        } catch (e: Exception) {
            return DecodeResult.Failure("Could not read image data")
        }
        if (bytes.isEmpty()) return DecodeResult.Failure("The file is empty")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return DecodeResult.Failure("The selected file is not a valid image")
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return DecodeResult.Failure("The image could not be decoded")
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            DecodeResult.Success(applyExifOrientation(raw, orientation))
        } catch (e: OutOfMemoryError) {
            DecodeResult.Failure("The image is too large for this device")
        }
    }

    /** Smallest power-of-two inSampleSize bringing both dims <= [maxDimension]. */
    fun computeSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        return sample
    }
}

/**
 * Composes a decoded banner into a final output frame of exactly
 * [outWidth]×[outHeight] pixels. Pixels not covered by the image are black.
 */
object FrameComposer {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Darkening applied over the blurred backdrop so the image stays readable. */
    private const val BACKDROP_DIM = 0x99000000.toInt()

    fun compose(source: Bitmap, outWidth: Int, outHeight: Int, mode: FitMode): Bitmap {
        val result = FrameGeometry.compute(source.width, source.height, outWidth, outHeight, mode)
        val frame = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.drawColor(android.graphics.Color.BLACK)
        if (mode == FitMode.FIT) drawBlurredBackdrop(canvas, source, outWidth, outHeight)
        canvas.drawBitmap(
            source,
            Rect(result.src.left, result.src.top, result.src.right, result.src.bottom),
            Rect(result.dst.left, result.dst.top, result.dst.right, result.dst.bottom),
            paint,
        )
        return frame
    }

    /**
     * Fills the frame with a heavily downscaled (therefore blurred) copy of
     * the image stretched to cover, dimmed underneath the sharp FIT image.
     * Only affects empty letterbox/pillarbox areas - the image itself is
     * still drawn whole and undistorted on top.
     */
    private fun drawBlurredBackdrop(canvas: Canvas, source: Bitmap, outW: Int, outH: Int) {
        // ~24px on the short edge is enough for a soft blur once upscaled.
        val ratio = source.width.toFloat() / source.height
        val (tw, th) = if (ratio >= 1f) {
            (24f * ratio).toInt().coerceAtLeast(8) to 24
        } else {
            24 to (24f / ratio).toInt().coerceAtLeast(8)
        }
        val tiny = Bitmap.createScaledBitmap(source, tw, th, true)
        val fill = FrameGeometry.compute(tw, th, outW, outH, FitMode.FILL)
        canvas.drawBitmap(
            tiny,
            Rect(fill.src.left, fill.src.top, fill.src.right, fill.src.bottom),
            Rect(0, 0, outW, outH),
            paint,
        )
        tiny.recycle()
        canvas.drawColor(BACKDROP_DIM)
    }
}
