package com.audiocast.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiocast.app.data.AudioQuality
import com.audiocast.app.data.FitMode
import com.audiocast.app.data.StreamOrientation
import com.audiocast.app.viewmodel.AudioCastViewModel

/**
 * Screen 1 - Setup: stream key, banner selection, orientation, fit mode,
 * audio configuration. Everything needed before entering the studio.
 */
@Composable
fun SetupScreen(
    viewModel: AudioCastViewModel,
    onContinue: () -> Unit,
) {
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val defaultFrame by viewModel.defaultFrame.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasSavedKey.collectAsStateWithLifecycle()

    var keyInput by rememberSaveable { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onBannerPicked) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Stream Audio Cast", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Static image + live audio to Twitch",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- Stream key -------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Twitch stream key", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (hasKey) "New key (replace saved)" else "Paste your stream key") },
                    visualTransformation = if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key",
                            )
                        }
                    },
                )
                Text(
                    "Find it in Twitch Creator Dashboard → Settings → Stream → " +
                        "Primary Stream key. Stored encrypted on this device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveStreamKey(keyInput)
                            keyInput = ""
                        },
                        enabled = keyInput.isNotBlank(),
                    ) { Text(if (hasKey) "Update key" else "Save key") }
                    if (hasKey) {
                        Text(
                            "Key saved ✓",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }

        // --- Banner -----------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stream banner", style = MaterialTheme.typography.titleMedium)
                banner?.let { b ->
                    BannerPreview(b.previewFrame, config.orientation)
                    b.displayName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } ?: run {
                    defaultFrame?.let { BannerPreview(it, config.orientation) }
                    Text(
                        "Optional: pick any image from your device and it becomes the " +
                            "video of your stream — empty areas are black. Without one, " +
                            "the branded Stream Audio Cast banner above is streamed. " +
                            "The image never leaves your device except in the stream.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = {
                    picker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (banner == null) "Choose Banner" else "Change Banner")
                }
            }
        }

        // --- Layout -----------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Output layout", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    StreamOrientation.entries.forEachIndexed { i, o ->
                        SegmentedButton(
                            selected = config.orientation == o,
                            onClick = { viewModel.setOrientation(o) },
                            shape = SegmentedButtonDefaults.itemShape(i, StreamOrientation.entries.size),
                        ) { Text(if (o == StreamOrientation.LANDSCAPE) "Horizontal" else "Vertical") }
                    }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FitMode.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = config.fitMode == f,
                            onClick = { viewModel.setFitMode(f) },
                            shape = SegmentedButtonDefaults.itemShape(i, FitMode.entries.size),
                        ) { Text(f.label) }
                    }
                }
            }
        }

        // --- Audio ------------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Audio", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AudioQuality.entries.forEachIndexed { i, q ->
                        SegmentedButton(
                            selected = config.audioQuality == q,
                            onClick = { viewModel.setAudioQuality(q) },
                            shape = SegmentedButtonDefaults.itemShape(i, AudioQuality.entries.size),
                        ) { Text("${q.bitrateBps / 1000}k") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Stereo microphone", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.stereo,
                        onCheckedChange = { viewModel.setStereo(it) },
                    )
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = hasKey,
        ) {
            Text("Continue to Stream Studio", textAlign = TextAlign.Center)
        }
        if (!hasKey) {
            Text(
                "Save your stream key to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BannerPreview(frame: Bitmap, orientation: StreamOrientation, modifier: Modifier = Modifier) {
    val aspect = orientation.width.toFloat() / orientation.height
    val sizeMod = if (aspect >= 1f) {
        Modifier.fillMaxWidth().aspectRatio(aspect)
    } else {
        // Portrait frames would be too tall at full width; cap the height.
        Modifier.height(320.dp).aspectRatio(aspect)
    }
    Image(
        bitmap = frame.asImageBitmap(),
        contentDescription = "Banner preview as streamed",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .then(sizeMod)
            .clip(RoundedCornerShape(12.dp)),
    )
}
