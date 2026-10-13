package com.audiocast.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiocast.app.streaming.StreamController
import com.audiocast.app.ui.components.AudioLevelBar
import com.audiocast.app.ui.components.StreamStatusChip
import com.audiocast.app.ui.components.formatDuration
import com.audiocast.app.viewmodel.AudioCastViewModel
import kotlinx.coroutines.delay

/**
 * Screen 2 - Stream Studio: live preview, start/stop, live banner swap,
 * mute, audio meter, duration and network stats.
 */
@Composable
fun StudioScreen(
    viewModel: AudioCastViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val level by viewModel.audioLevel.collectAsStateWithLifecycle()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.liveSinceMs) {
        while (status.liveSinceMs > 0) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) viewModel.startStream()
    }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onBannerPicked) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreamStatusChip(status)
            Text(
                formatDuration(status.liveSinceMs, nowMs),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        status.error?.let {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            )) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // WYSIWYG preview: the exact frame going to the encoder.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            banner?.let { BannerPreview(it.previewFrame, config.orientation) }
                ?: Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No banner selected",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        }

        AudioLevelBar(if (status.muted) 0f else level)

        // --- Primary controls -------------------------------------------
        val isActive = status.isActive
        Button(
            onClick = {
                if (isActive) {
                    viewModel.stopStream()
                } else {
                    if (micGranted) viewModel.startStream()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            ),
            enabled = status.state != StreamController.State.STOPPING &&
                status.state != StreamController.State.PREPARING,
        ) {
            Text(
                when (status.state) {
                    StreamController.State.CONNECTING -> "Connecting…"
                    StreamController.State.RECONNECTING -> "Reconnecting… (tap to stop)"
                    StreamController.State.STOPPING -> "Stopping…"
                    StreamController.State.PREPARING -> "Preparing…"
                    else -> if (isActive) "Stop Stream" else "Start Stream"
                },
            )
        }

        if (!micGranted) {
            Text(
                "Microphone permission is required to stream live audio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { bannerPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Change Banner")
            }
            OutlinedButton(
                onClick = { viewModel.setMuted(!status.muted) },
                modifier = Modifier.weight(1f),
                enabled = isActive,
            ) {
                Icon(
                    if (status.muted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (status.muted) "Unmute" else "Mute")
            }
        }

        // --- Network stats ----------------------------------------------
        if (status.isLive || status.state == StreamController.State.RECONNECTING) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Network", style = MaterialTheme.typography.titleSmall)
                    StatRow("Outgoing bitrate", "${status.bitrateKbps} kbps")
                    StatRow("Video frames sent", "${status.sentVideoFrames}")
                    StatRow("Video frames dropped", "${status.droppedVideoFrames}")
                    StatRow("Audio frames sent", "${status.sentAudioFrames}")
                    StatRow("Audio frames dropped", "${status.droppedAudioFrames}")
                    if (status.congested) {
                        Text(
                            "Upload congested - bitrate exceeds your network. " +
                                "Lower the video bitrate in Settings.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
