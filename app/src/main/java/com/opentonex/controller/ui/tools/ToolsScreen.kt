package com.opentonex.controller.ui.tools

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val MIN_BPM = 40
private const val MAX_BPM = 240
private const val TAP_WINDOW_MS = 2500L

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    var flashBeat by remember { mutableStateOf(false) }
    val tapTimestamps = remember { mutableListOf<Long>() }

    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    val latestBpm = rememberUpdatedState(bpm)
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            flashBeat = true
            delay(80)
            flashBeat = false
            delay((60_000L / latestBpm.value) - 80)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Metronomo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = if (flashBeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {}

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { bpm = (bpm - 1).coerceIn(MIN_BPM, MAX_BPM) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Diminuir BPM")
            }
            Text(text = "$bpm", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { bpm = (bpm + 1).coerceIn(MIN_BPM, MAX_BPM) }) {
                Icon(Icons.Filled.Add, contentDescription = "Aumentar BPM")
            }
        }
        Text(text = "BPM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Slider(
            value = bpm.toFloat(),
            onValueChange = { bpm = it.toInt().coerceIn(MIN_BPM, MAX_BPM) },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.weight(1f),
                colors = if (isPlaying) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = if (isPlaying) "Parar" else "Tocar", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    tapTimestamps.removeAll { now - it > TAP_WINDOW_MS }
                    tapTimestamps.add(now)
                    if (tapTimestamps.size >= 2) {
                        val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
                        val avgMs = intervals.average()
                        if (avgMs > 0) {
                            bpm = (60_000.0 / avgMs).toInt().coerceIn(MIN_BPM, MAX_BPM)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "TAP")
            }
        }

        Text(
            text = "Clique sonoro local via ToneGenerator, sem depender do pedal.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Afinador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Em breve: deteccao de pitch via microfone do aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
