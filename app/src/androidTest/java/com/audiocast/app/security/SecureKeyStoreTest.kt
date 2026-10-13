package com.audiocast.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureKeyStoreTest {

    private lateinit var store: SecureKeyStore

    @Before
    fun setUp() {
        store = SecureKeyStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clearStreamKey()
    }

    @Test
    fun saveThenReadRoundTripsTheKey() {
        store.saveStreamKey("live_12345_secret")
        assertTrue(store.hasStreamKey())
        assertEquals("live_12345_secret", store.getStreamKey())
    }

    @Test
    fun clearRemovesTheKey() {
        store.saveStreamKey("live_12345_secret")
        store.clearStreamKey()
        assertFalse(store.hasStreamKey())
        assertNull(store.getStreamKey())
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(store.getStreamKey())
        assertFalse(store.hasStreamKey())
    }

    @Test
    fun overwriteReplacesKey() {
        store.saveStreamKey("first")
        store.saveStreamKey("second")
        assertEquals("second", store.getStreamKey())
    }
}
