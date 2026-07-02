package com.opentonex.controller.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.opentonex.controller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_BPM = 40
private const val MAX_BPM = 240
private const val TAP_WINDOW_MS = 2500L
private const val TUNER_SAMPLE_RATE = 44_100
private const val TUNER_WINDOW_SIZE = 4096

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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            MetronomePanel(
                bpm = bpm,
                isPlaying = isPlaying,
                flashBeat = flashBeat,
                onBpmChange = { bpm = it.coerceIn(MIN_BPM, MAX_BPM) },
                onTogglePlaying = { isPlaying = !isPlaying },
                onTap = {
                    val now = System.currentTimeMillis()
                    tapTimestamps.removeAll { now - it > TAP_WINDOW_MS }
                    tapTimestamps.add(now)
                    if (tapTimestamps.size >= 2) {
                        val avgMs = tapTimestamps.zipWithNext { a, b -> b - a }.average()
                        if (avgMs > 0) {
                            bpm = (60_000.0 / avgMs).toInt().coerceIn(MIN_BPM, MAX_BPM)
                        }
                    }
                }
            )
        }
        item { TunerPanel() }
    }
}

@Composable
private fun MetronomePanel(
    bpm: Int,
    isPlaying: Boolean,
    flashBeat: Boolean,
    onBpmChange: (Int) -> Unit,
    onTogglePlaying: () -> Unit,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.metronome_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = if (flashBeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {}

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { onBpmChange(bpm - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.bpm_decrease))
            }
            Text(text = "$bpm", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onBpmChange(bpm + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bpm_increase))
            }
        }
        Text(text = "BPM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.toInt()) },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onTogglePlaying,
                modifier = Modifier.weight(1f),
                colors = if (isPlaying) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = if (isPlaying) stringResource(R.string.stop) else stringResource(R.string.play),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            OutlinedButton(onClick = onTap, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.tap))
            }
        }

        Text(
            text = stringResource(R.string.metronome_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TunerPanel() {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isListening by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(TuningPreset.STANDARD) }
    var selectedStringIndex by remember { mutableStateOf<Int?>(null) }
    var detectedFrequency by remember { mutableStateOf<Double?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) isListening = true
    }

    LaunchedEffect(isListening, hasMicPermission) {
        if (!isListening || !hasMicPermission) return@LaunchedEffect
        readPitchFromMicrophone { frequency ->
            detectedFrequency = frequency
        }
    }

    val reading = detectedFrequency?.let { frequency ->
        readingFor(frequency, selectedPreset, selectedStringIndex)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.tuner_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            isListening = !isListening
                            if (isListening) detectedFrequency = null
                        }
                    }
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Text(
                        text = if (isListening) stringResource(R.string.tuner_stop) else stringResource(R.string.tuner_start),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (!hasMicPermission) {
                Text(
                    text = stringResource(R.string.tuner_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(text = stringResource(R.string.tuner_permission_button))
                }
            }

            Text(
                text = stringResource(R.string.tuner_common_tunings),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(TuningPreset.entries) { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = {
                            selectedPreset = preset
                            selectedStringIndex = null
                            detectedFrequency = null
                        },
                        label = { Text(text = stringResource(preset.labelRes())) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.tuner_string_targets),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    FilterChip(
                        selected = selectedStringIndex == null,
                        onClick = { selectedStringIndex = null },
                        label = { Text(text = stringResource(R.string.tuner_auto)) }
                    )
                }
                items(selectedPreset.notes.indices.toList()) { index ->
                    val note = selectedPreset.notes[index]
                    FilterChip(
                        selected = selectedStringIndex == index,
                        onClick = { selectedStringIndex = index },
                        label = { Text(text = note.label) }
                    )
                }
            }

            TunerReadout(reading = reading, isListening = isListening)
            Text(
                text = stringResource(R.string.tuner_reference, 440),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TunerReadout(reading: TunerReading?, isListening: Boolean) {
    val cents = reading?.cents?.coerceIn(-50.0, 50.0) ?: 0.0
    val progress = ((cents + 50.0) / 100.0).toFloat()
    val statusText = when {
        reading == null && isListening -> stringResource(R.string.tuner_listening)
        reading == null -> stringResource(R.string.tuner_play_string)
        reading.isInTune -> stringResource(R.string.tuner_in_tune)
        reading.cents < 0 -> stringResource(R.string.tuner_flat)
        else -> stringResource(R.string.tuner_sharp)
    }
    val statusColor = when {
        reading?.isInTune == true -> Color(0xFF5CFF6A)
        reading == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = reading?.target?.label ?: "--",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "-50c", style = MaterialTheme.typography.labelSmall)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (abs(cents) <= 5.0) Color(0xFF5CFF6A) else MaterialTheme.colorScheme.outline)
                    .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
            )
            Text(text = "+50c", style = MaterialTheme.typography.labelSmall)
        }
        val frequencyText = reading?.let {
            stringResource(R.string.tuner_detected_frequency, it.detectedFrequencyHz)
        } ?: "-- Hz"
        val centsText = reading?.let { "${it.cents.roundToInt()} c" } ?: "-- c"
        Text(
            text = "$frequencyText  |  $centsText",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@StringRes
private fun TuningPreset.labelRes(): Int = when (this) {
    TuningPreset.STANDARD -> R.string.tuning_standard
    TuningPreset.DROP_D -> R.string.tuning_drop_d
    TuningPreset.HALF_STEP_DOWN -> R.string.tuning_half_step_down
    TuningPreset.D_STANDARD -> R.string.tuning_d_standard
    TuningPreset.OPEN_G -> R.string.tuning_open_g
    TuningPreset.DADGAD -> R.string.tuning_dadgad
}

@SuppressLint("MissingPermission")
private suspend fun readPitchFromMicrophone(onPitch: (Double?) -> Unit) = withContext(Dispatchers.IO) {
    val minimumBufferSize = AudioRecord.getMinBufferSize(
        TUNER_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        TUNER_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minimumBufferSize, TUNER_WINDOW_SIZE * 2)
    )
    val buffer = ShortArray(TUNER_WINDOW_SIZE)
    try {
        audioRecord.startRecording()
        while (currentCoroutineContext().isActive) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                val pitch = detectPitchHz(buffer.copyOf(read), TUNER_SAMPLE_RATE)
                withContext(Dispatchers.Main) {
                    onPitch(pitch)
                }
            }
        }
    } finally {
        runCatching { audioRecord.stop() }
        audioRecord.release()
    }
}
