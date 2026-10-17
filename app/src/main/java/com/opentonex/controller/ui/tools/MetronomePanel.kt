package com.opentonex.controller.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.ui.components.TusbSegmentedRow
import com.opentonex.controller.ui.theme.KnobBodyDark
import com.opentonex.controller.ui.theme.KnobBodyLight
import com.opentonex.controller.ui.theme.KnobBodyMid
import com.opentonex.controller.ui.theme.KnobTrack
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurfaceHigh
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Metronomo da aba Tools: compasso, indicador de tempos, roda de BPM com +/-, tom, volume,
 * acento, TAP e PLAY. Todo o audio e' local (AudioTrack); nada e' enviado ao pedal.
 */
@Composable
fun MetronomePanel(modifier: Modifier = Modifier) {
    var bpm by rememberSaveable { mutableIntStateOf(120) }
    var signatureIndex by rememberSaveable { mutableIntStateOf(TimeSignature.FOUR_FOUR.ordinal) }
    var toneHz by rememberSaveable { mutableFloatStateOf(1000f) }
    var volume by rememberSaveable { mutableFloatStateOf(0.8f) }
    var accentOn by rememberSaveable { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(-1) }
    val signature = TimeSignature.entries[signatureIndex.coerceIn(0, TimeSignature.entries.lastIndex)]

    val tapTempo = remember { TapTempo() }
    val engine = remember { MetronomeEngine() }
    DisposableEffect(engine) { onDispose { engine.stop() } }
    SideEffect {
        engine.bpm = bpm
        engine.beatsPerBar = signature.beats
        engine.toneHz = toneHz.toDouble()
        engine.volume = volume
        engine.accentEnabled = accentOn
    }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            engine.start { beat -> currentBeat = beat }
        } else {
            engine.stop()
            currentBeat = -1
        }
    }

    fun setBpm(value: Int) {
        bpm = value.coerceIn(MIN_BPM, MAX_BPM)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.metronome_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.metronome_signature).uppercase(),
            style = MonoLabelStyle,
            color = ToneXOnSurfaceMuted
        )
        TusbSegmentedRow(
            options = TimeSignature.entries.map { it.label },
            selectedIndex = signature.ordinal,
            modifier = Modifier.fillMaxWidth(),
            onSelect = { signatureIndex = it }
        )

        BeatDots(beats = signature.beats, currentBeat = currentBeat, accentOn = accentOn)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { setBpm(bpm - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.bpm_decrease))
            }
            BpmWheel(bpm = bpm, onBpmChange = ::setBpm)
            IconButton(onClick = { setBpm(bpm + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bpm_increase))
            }
        }

        LabeledSlider(
            label = stringResource(R.string.metronome_tone),
            valueText = "${toneHz.roundToInt()} Hz",
            value = toneHz,
            range = MetronomeClick.MIN_TONE_HZ..MetronomeClick.MAX_TONE_HZ,
            onValueChange = { toneHz = it }
        )
        LabeledSlider(
            label = stringResource(R.string.metronome_volume),
            valueText = "${(volume * 100).roundToInt()}%",
            value = volume,
            range = 0f..1f,
            onValueChange = { volume = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.metronome_accent).uppercase(),
                style = MonoLabelStyle,
                color = ToneXOnSurfaceMuted
            )
            Switch(
                checked = accentOn,
                onCheckedChange = { accentOn = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ToneXAccent,
                    checkedThumbColor = Color(0xFF111111)
                )
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { isPlaying = !isPlaying },
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
            OutlinedButton(
                onClick = { tapTempo.tap(System.currentTimeMillis())?.let(::setBpm) },
                modifier = Modifier.weight(1f)
            ) {
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

/** Uma bolinha por tempo do compasso; o tempo atual acende (o primeiro com a cor de acento). */
@Composable
private fun BeatDots(beats: Int, currentBeat: Int, accentOn: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(beats) { index ->
            val lit = index == currentBeat
            val color = when {
                !lit -> ToneXSurfaceHigh
                index == 0 && accentOn -> ToneXAccent
                else -> Color.White
            }
            Box(modifier = Modifier.size(if (index == 0) 18.dp else 14.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label.uppercase(), style = MonoLabelStyle, color = ToneXOnSurfaceMuted)
            Text(text = valueText, style = MonoLabelStyle, color = ToneXOnSurfaceMuted)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ToneXAccent,
                activeTrackColor = ToneXAccent,
                inactiveTrackColor = ToneXSurfaceHigh
            )
        )
    }
}

/**
 * Roda de BPM: arrastar em volta do centro gira a roda (uma volta = 60 BPM). Os tracos
 * giram junto com o valor e o entalhe fixo no topo marca a posicao atual.
 */
@Composable
private fun BpmWheel(bpm: Int, onBpmChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val latestBpm = rememberUpdatedState(bpm)
    val latestOnChange = rememberUpdatedState(onBpmChange)
    Box(
        modifier = modifier
            .size(200.dp)
            .pointerInput(Unit) {
                var lastAngle = 0f
                var accumulated = 0f
                var startBpm = 0
                detectDragGestures(
                    onDragStart = { offset ->
                        lastAngle = BpmWheelMath.angleDegrees(offset.x, offset.y, size.width / 2f, size.height / 2f)
                        accumulated = 0f
                        startBpm = latestBpm.value
                    }
                ) { change, _ ->
                    change.consume()
                    val angle = BpmWheelMath.angleDegrees(
                        change.position.x, change.position.y, size.width / 2f, size.height / 2f
                    )
                    accumulated += BpmWheelMath.deltaBpm(lastAngle, angle)
                    lastAngle = angle
                    latestOnChange.value((startBpm + accumulated).roundToInt())
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f
            val ring = outer * 0.10f
            val bodyR = outer - ring * 1.4f
            drawCircle(color = KnobTrack, radius = outer - ring / 2f, center = center, style = Stroke(width = ring))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(KnobBodyLight, KnobBodyMid, KnobBodyDark),
                    center = Offset(center.x - bodyR * 0.4f, center.y - bodyR * 0.48f),
                    radius = bodyR * 2f
                ),
                radius = bodyR,
                center = center
            )
            // Tracos a cada 10 graus, girados pelo valor (6 graus por BPM).
            val spin = bpm * 6.0
            for (i in 0 until 36) {
                val angle = Math.toRadians(spin + i * 10.0)
                val inner = bodyR * 0.82f
                val tip = bodyR * 0.94f
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(center.x + (cos(angle) * inner).toFloat(), center.y + (sin(angle) * inner).toFloat()),
                    end = Offset(center.x + (cos(angle) * tip).toFloat(), center.y + (sin(angle) * tip).toFloat()),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
            // Entalhe fixo no topo.
            drawLine(
                color = ToneXAccent,
                start = Offset(center.x, center.y - bodyR * 0.74f),
                end = Offset(center.x, center.y - bodyR * 1.0f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$bpm", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "BPM", style = MonoLabelStyle, color = ToneXOnSurfaceMuted)
        }
    }
}
