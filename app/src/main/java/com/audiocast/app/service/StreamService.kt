package com.audiocast.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.audiocast.app.AudioCastApp
import com.audiocast.app.MainActivity
import com.audiocast.app.R
import com.audiocast.app.streaming.StreamController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the live stream alive while the app is in the
 * background. Foreground-service type is "microphone" - the only device input
 * the app uses. There is no screen capture and no camera usage.
 */
class StreamService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var statusJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreamingAndSelf()
                return START_NOT_STICKY
            }
            ACTION_MUTE -> {
                val c = controller()
                c.setMuted(!c.status.value.muted)
                updateNotification(c.status.value)
                return START_STICKY
            }
            ACTION_UPDATE -> {
                updateNotification(controller().status.value)
                return START_STICKY
            }
            else -> {
                goForeground()
                observeStatus()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        statusJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun controller(): StreamController =
        (application as AudioCastApp).streamController

    private fun goForeground() {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(controller().status.value), type)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(controller().status.value))
        }
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun observeStatus() {
        if (statusJob != null) return
        statusJob = serviceScope.launch {
            controller().status.collectLatest { status ->
                updateNotification(status)
                if (status.state == StreamController.State.DISCONNECTED ||
                    status.state == StreamController.State.ERROR
                ) {
                    stopSelf()
                }
            }
        }
    }

    private fun stopStreamingAndSelf() {
        serviceScope.launch {
            controller().stopStreaming()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(status: StreamController.Status) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: StreamController.Status): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val muteIntent = PendingIntent.getService(
            this, 2,
            Intent(this, StreamService::class.java).setAction(ACTION_MUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (status.state) {
            StreamController.State.LIVE ->
                if (status.muted) "Stream Audio Cast is live (muted)" else "Stream Audio Cast is live"
            StreamController.State.RECONNECTING -> "Stream Audio Cast reconnecting…"
            StreamController.State.CONNECTING, StreamController.State.PREPARING ->
                "Stream Audio Cast connecting…"
            StreamController.State.STOPPING -> "Stream Audio Cast stopping…"
            StreamController.State.ERROR -> "Stream Audio Cast stream error"
            StreamController.State.DISCONNECTED -> "Stream Audio Cast stopped"
        }
        val text = when (status.state) {
            StreamController.State.LIVE -> "Streaming ${status.bitrateKbps} kbps to Twitch"
            StreamController.State.RECONNECTING -> "Attempt ${status.reconnectAttempt}"
            else -> "Microphone stream"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(status.isActive)
            .setOnlyAlertOnce(true)
            .addAction(0, if (status.muted) "Unmute" else "Mute", muteIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.audiocast.app.action.START"
        const val ACTION_STOP = "com.audiocast.app.action.STOP"
        const val ACTION_UPDATE = "com.audiocast.app.action.UPDATE"
        const val ACTION_MUTE = "com.audiocast.app.action.MUTE"
        private const val CHANNEL_ID = "audiocast_streaming"
        private const val NOTIFICATION_ID = 0xA0C1
        private const val WAKELOCK_TAG = "audiocast:stream"
        private const val WAKELOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        fun startIntent(context: Context): Intent =
            Intent(context, StreamService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, StreamService::class.java).setAction(ACTION_STOP)
    }
}
