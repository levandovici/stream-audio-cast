package com.audiocast.app

import android.app.Application
import com.audiocast.app.data.SettingsRepository
import com.audiocast.app.security.SecureKeyStore
import com.audiocast.app.streaming.StreamController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AudioCastApp : Application() {

    /** Long-lived scope used by the streaming pipeline; survives UI recreation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var secureKeyStore: SecureKeyStore
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var streamController: StreamController
        private set

    override fun onCreate() {
        super.onCreate()
        secureKeyStore = SecureKeyStore(this)
        settingsRepository = SettingsRepository(this)
        streamController = StreamController(this, appScope)
    }
}
