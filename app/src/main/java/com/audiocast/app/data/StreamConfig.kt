package com.audiocast.app.data

/** Output orientation of the encoded video track. */
enum class StreamOrientation(val width: Int, val height: Int, val label: String) {
    LANDSCAPE(1280, 720, "Horizontal 1280×720"),
    PORTRAIT(720, 1280, "Vertical 720×1280"),
}

/** How the selected image is mapped into the output frame. */
enum class FitMode(val label: String) {
    /** Entire image visible; unused area filled with black. */
    FIT("Fit (black bars)"),

    /** Image scaled to cover the frame; overflow is cropped. */
    FILL("Fill (crop)"),
}

enum class AudioQuality(
    val bitrateBps: Int,
    val label: String,
) {
    STANDARD(96_000, "Standard · 96 kbps"),
    HIGH(128_000, "High · 128 kbps"),
    STUDIO(160_000, "Studio · 160 kbps"),
}

/** Immutable snapshot of the streaming configuration. */
data class StreamConfig(
    val orientation: StreamOrientation = StreamOrientation.LANDSCAPE,
    val fitMode: FitMode = FitMode.FIT,
    val videoBitrateBps: Int = 1_000_000,
    val fps: Int = 30,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val sampleRate: Int = 48_000,
    val stereo: Boolean = false,
    val ingestServer: String = "rtmp://live.twitch.tv/app",
    val maxReconnectAttempts: Int = 8,
    val socketTimeoutMs: Long = 5_000,
) {
    val width: Int get() = orientation.width
    val height: Int get() = orientation.height
}
