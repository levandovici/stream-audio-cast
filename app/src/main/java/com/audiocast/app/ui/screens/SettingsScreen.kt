package com.audiocast.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiocast.app.data.AudioQuality
import com.audiocast.app.data.FitMode
import com.audiocast.app.data.StreamOrientation
import com.audiocast.app.streaming.TwitchIngest
import com.audiocast.app.viewmodel.AudioCastViewModel

/**
 * Screen 3 - Settings: stream defaults, reconnect behavior, key management
 * and privacy information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AudioCastViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasSavedKey.collectAsStateWithLifecycle()
    var ingestInput by rememberSaveable { mutableStateOf<String?>(null) }

    val locked = status.isActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        if (locked) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Text(
                    "A stream is active. Changes apply to the next stream.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // --- Defaults -----------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stream defaults", style = MaterialTheme.typography.titleMedium)

                Text("Orientation", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StreamOrientation.entries.forEachIndexed { i, o ->
                        SegmentedButton(
                            selected = config.orientation == o,
                            onClick = { viewModel.setOrientation(o) },
                            shape = SegmentedButtonDefaults.itemShape(i, StreamOrientation.entries.size),
                        ) { Text(if (o == StreamOrientation.LANDSCAPE) "Horizontal" else "Vertical") }
                    }
                }

                Text("Image fit", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FitMode.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = config.fitMode == f,
                            onClick = { viewModel.setFitMode(f) },
                            shape = SegmentedButtonDefaults.itemShape(i, FitMode.entries.size),
                        ) { Text(f.label) }
                    }
                }

                Text("Audio quality", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AudioQuality.entries.forEachIndexed { i, q ->
                        SegmentedButton(
                            selected = config.audioQuality == q,
                            onClick = { viewModel.setAudioQuality(q) },
                            shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                        ) { Text(q.label.substringBefore(' ')) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Stereo", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = config.stereo, onCheckedChange = viewModel::setStereo)
                }
            }
        }

        // --- Quality ------------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Video quality", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bitrate: ${config.videoBitrateBps / 1000} kbps",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = (config.videoBitrateBps / 1000).toFloat(),
                    onValueChange = { viewModel.setVideoBitrateKbps(it.toInt()) },
                    valueRange = 250f..4000f,
                    steps = 14,
                )
                Text(
                    "A static image needs little bandwidth. 500–1500 kbps is usually enough.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Frame rate", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(24, 30, 60).forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = config.fps == f,
                            onClick = { viewModel.setFps(f) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text("$f fps") }
                    }
                }
            }
        }

        // --- Ingest / reconnect ------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connection", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = ingestInput ?: config.ingestServer,
                    onValueChange = {
                        ingestInput = it
                        if (TwitchIngest.isSupportedServer(it)) viewModel.setIngestServer(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("RTMP ingest server") },
                    supportingText = {
                        Text("Default: ${TwitchIngest.DEFAULT_SERVER}. Regional ingest " +
                            "servers are listed in the Twitch dashboard.")
                    },
                    isError = ingestInput?.let { !TwitchIngest.isSupportedServer(it) } == true,
                )
                Text(
                    "Reconnect attempts: ${config.maxReconnectAttempts}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = config.maxReconnectAttempts.toFloat(),
                    onValueChange = { viewModel.setMaxReconnectAttempts(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19,
                )
            }
        }

        // --- Account ------------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (hasKey) "Stream key saved (encrypted)" else "No stream key saved",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = viewModel::clearStreamKey,
                        enabled = hasKey && !locked,
                    ) { Text("Clear key") }
                }
            }
        }

        // --- Privacy ------------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• The stream key is encrypted with Android Keystore and only sent " +
                        "to the Twitch ingest server during streaming.\n" +
                        "• Audio is captured from your microphone only when you start a " +
                        "stream, and only sent to Twitch.\n" +
                        "• Banner images stay on this device; they are only encoded into " +
                        "the outgoing stream.\n" +
                        "• AudioCast never records your screen, never uses your camera " +
                        "and never saves audio or video recordings.\n" +
                        "• No analytics, no tracking, no third-party data collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "AudioCast 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
