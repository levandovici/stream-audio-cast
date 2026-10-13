package com.audiocast.app.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchIngestTest {

    @Test
    fun `builds endpoint with single separator`() {
        assertEquals(
            "rtmp://live.twitch.tv/app/live_abc",
            TwitchIngest.buildEndpoint("rtmp://live.twitch.tv/app", "live_abc")
        )
        assertEquals(
            "rtmp://live.twitch.tv/app/live_abc",
            TwitchIngest.buildEndpoint("rtmp://live.twitch.tv/app/", "live_abc")
        )
        assertEquals(
            "rtmp://live.twitch.tv/app/live_abc",
            TwitchIngest.buildEndpoint("  rtmp://live.twitch.tv/app//  ", "live_abc")
        )
    }

    @Test
    fun `blank server falls back to default`() {
        assertEquals(
            "rtmp://live.twitch.tv/app/k",
            TwitchIngest.buildEndpoint("   ", "k")
        )
    }

    @Test
    fun `accepts rtmp and rtmps schemes only`() {
        assertTrue(TwitchIngest.isSupportedServer("rtmp://live.twitch.tv/app"))
        assertTrue(TwitchIngest.isSupportedServer("rtmps://example.com/app"))
        assertFalse(TwitchIngest.isSupportedServer("http://example.com"))
        assertFalse(TwitchIngest.isSupportedServer("example.com/app"))
        assertFalse(TwitchIngest.isSupportedServer(""))
    }

    @Test
    fun `sanitize removes the stream key from urls`() {
        val endpoint = "rtmp://live.twitch.tv/app/live_secret123"
        assertEquals(
            "rtmp://live.twitch.tv/app/${TwitchIngest.MASK}",
            TwitchIngest.sanitize(endpoint, "live_secret123")
        )
        assertFalse(TwitchIngest.sanitize(endpoint, "live_secret123").contains("live_secret123"))
    }

    @Test
    fun `sanitize never returns the raw url when key is unknown`() {
        assertEquals(TwitchIngest.MASK, TwitchIngest.sanitize("rtmp://x/y", "other"))
    }
}
