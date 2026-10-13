package com.audiocast.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.audiocast.app.streaming.StreamController
import com.audiocast.app.ui.theme.LiveRed
import com.audiocast.app.ui.theme.SuccessGreen

/** Colored chip showing the current stream state. */
@Composable
fun StreamStatusChip(status: StreamController.Status, modifier: Modifier = Modifier) {
    val (color, label) = when (status.state) {
        StreamController.State.LIVE -> LiveRed to "LIVE"
        StreamController.State.RECONNECTING -> Color(0xFFFFA726) to "RECONNECTING ${status.reconnectAttempt}"
        StreamController.State.CONNECTING -> Color(0xFFFFA726) to "CONNECTING"
        StreamController.State.PREPARING -> Color(0xFFFFA726) to "PREPARING"
        StreamController.State.STOPPING -> Color(0xFFFFA726) to "STOPPING"
        StreamController.State.ERROR -> MaterialTheme.colorScheme.error to "ERROR"
        StreamController.State.DISCONNECTED -> SuccessGreen.copy(alpha = 0.6f) to "OFFLINE"
    }
    val animated by animateColorAsState(color, label = "chip")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animated)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Horizontal peak audio level meter. */
@Composable
fun AudioLevelBar(level: Float, modifier: Modifier = Modifier) {
    val clamped = level.coerceIn(0f, 1f)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Microphone", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(clamped * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val barColor = when {
                clamped > 0.85f -> LiveRed
                clamped > 0.6f -> Color(0xFFFFA726)
                else -> SuccessGreen
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
        }
    }
}

/** mm:ss or hh:mm:ss duration from a start epoch. */
fun formatDuration(liveSinceMs: Long, nowMs: Long): String {
    if (liveSinceMs <= 0L) return "00:00"
    val total = ((nowMs - liveSinceMs).coerceAtLeast(0L)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
