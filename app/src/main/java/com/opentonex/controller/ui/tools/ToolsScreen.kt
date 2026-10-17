package com.opentonex.controller.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.opentonex.controller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TUNER_SAMPLE_RATE = 44_100
private const val TUNER_WINDOW_SIZE = 4096

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { MetronomePanel() }
        item { TunerPanel() }
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
