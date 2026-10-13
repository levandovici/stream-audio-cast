package com.audiocast.app.streaming

/**
 * Builds Twitch RTMP ingest endpoints and produces log-safe representations
 * that never contain the stream key.
 */
object TwitchIngest {

    const val DEFAULT_SERVER = "rtmp://live.twitch.tv/app"
    const val MASK = "***"

    fun normalizeServer(server: String): String {
        var s = server.trim().ifBlank { DEFAULT_SERVER }
        // Collapse any trailing slashes so join produces exactly one separator.
        while (s.endsWith('/')) s = s.dropLast(1)
        return s
    }

    fun isSupportedServer(server: String): Boolean {
        val s = server.trim().lowercase()
        return s.startsWith("rtmp://") || s.startsWith("rtmps://")
    }

    /** Endpoint handed to the RTMP client. Contains the stream key — never log it. */
    fun buildEndpoint(server: String, streamKey: String): String =
        "${normalizeServer(server)}/$streamKey"

    /**
     * Returns [url] with the stream key removed so it can be shown in errors.
     * If the key is not found the URL is still reduced to scheme + host + path root
     * so unexpected secrets cannot leak into UI or logs.
     */
    fun sanitize(url: String, streamKey: String): String =
        if (streamKey.isNotEmpty() && url.contains(streamKey)) url.replace(streamKey, MASK)
        else MASK
}
