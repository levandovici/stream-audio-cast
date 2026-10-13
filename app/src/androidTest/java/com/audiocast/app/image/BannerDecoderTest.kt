package com.audiocast.app.image

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audiocast.app.data.FitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BannerDecoderTest {

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        bitmap.compress(format, 90, out)
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun decodesPng() {
        val src = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val r = BannerDecoder.decodeStream(encode(src, Bitmap.CompressFormat.PNG))
        assertTrue(r is BannerDecoder.DecodeResult.Success)
        assertEquals(64, (r as BannerDecoder.DecodeResult.Success).bitmap.width)
    }

    @Test
    fun decodesJpeg() {
        val src = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val r = BannerDecoder.decodeStream(encode(src, Bitmap.CompressFormat.JPEG))
        assertTrue(r is BannerDecoder.DecodeResult.Success)
    }

    @Test
    fun decodesWebp() {
        val src = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val r = BannerDecoder.decodeStream(encode(src, Bitmap.CompressFormat.WEBP))
        assertTrue(r is BannerDecoder.DecodeResult.Success)
    }

    @Test
    fun rejectsCorruptData() {
        val r = BannerDecoder.decodeStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))
        assertTrue(r is BannerDecoder.DecodeResult.Failure)
    }

    @Test
    fun rejectsEmptyStream() {
        val r = BannerDecoder.decodeStream(ByteArrayInputStream(ByteArray(0)))
        assertTrue(r is BannerDecoder.DecodeResult.Failure)
    }

    @Test
    fun oversizedImagesAreDownsampled() {
        val src = Bitmap.createBitmap(6000, 4000, Bitmap.Config.ARGB_8888)
        val r = BannerDecoder.decodeStream(encode(src, Bitmap.CompressFormat.PNG), 2000)
        assertTrue(r is BannerDecoder.DecodeResult.Success)
        val b = (r as BannerDecoder.DecodeResult.Success).bitmap
        assertTrue(b.width <= 2000 && b.height <= 2000)
    }

    @Test
    fun sampleSizeHalvesUntilWithinCap() {
        assertEquals(1, BannerDecoder.computeSampleSize(1000, 800, 4096))
        assertEquals(2, BannerDecoder.computeSampleSize(6000, 4000, 4096))
        assertEquals(4, BannerDecoder.computeSampleSize(16000, 8000, 4096))
    }

    @Test
    fun composerProducesExactFrameSize() {
        val src = Bitmap.createBitmap(640, 200, Bitmap.Config.ARGB_8888)
        val frame = FrameComposer.compose(src, 1280, 720, FitMode.FIT)
        assertEquals(1280, frame.width)
        assertEquals(720, frame.height)
        val portrait = FrameComposer.compose(src, 720, 1280, FitMode.FILL)
        assertEquals(720, portrait.width)
        assertEquals(1280, portrait.height)
        src.recycle(); frame.recycle(); portrait.recycle()
    }

    @Test
    fun fitModeLeavesBlackBars() {
        // 2:1 red image in a 16:9 frame: top and bottom rows stay black.
        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        src.eraseColor(android.graphics.Color.RED)
        val frame = FrameComposer.compose(src, 1280, 720, FitMode.FIT)
        assertEquals(android.graphics.Color.BLACK, frame.getPixel(640, 5))
        assertEquals(android.graphics.Color.RED, frame.getPixel(640, 360))
        src.recycle(); frame.recycle()
    }
}
