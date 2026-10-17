package com.opentonex.controller.ui.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentonex.controller.R
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.protocol.GlobalSetting
import com.opentonex.controller.protocol.TonexGlobals

/** Volume master (comando proprio) e trim de entrada (StateData), gravados no pedal. */
@Composable
internal fun VolumeSettings(
    masterVolume: Float?,
    pedal: PedalState,
    onMasterVolumeChange: (Float) -> Unit,
    onGlobalSettingChange: (GlobalSetting, Float) -> Unit
) {
    if (masterVolume == null) {
        SettingCard(R.string.menu_master_volume, R.string.menu_master_volume_note) {
            Text(stringResource(R.string.menu_waiting_pedal), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        SliderSetting(
            titleRes = R.string.menu_master_volume,
            noteRes = R.string.menu_master_volume_note,
            value = masterVolume,
            range = TonexGlobals.MASTER_VOLUME_MIN..TonexGlobals.MASTER_VOLUME_MAX,
            step = 0.1f,
            format = { "%.1f".format(it) },
            onCommit = onMasterVolumeChange
        )
    }
    val trim = GlobalSetting.INPUT_TRIM
    SliderSetting(
        titleRes = R.string.menu_input_trim,
        noteRes = R.string.menu_input_trim_real,
        value = pedal.inputTrim,
        range = trim.min..trim.max,
        step = 0.5f,
        format = { "%+.1f dB".format(it) },
        onCommit = { onGlobalSettingChange(trim, it) }
    )
}

/**
 * Encaixa o valor do slider no passo do ajuste. O Slider com `steps` entregava o passo com
 * ruido de float (bancada: 5.0000005 em vez de 5.0) e desenhava um ponto por passo na trilha.
 */
internal fun snapToStep(value: Float, step: Float): Float = Math.round(value / step) / (1f / step)

/** Referencia do afinador e o que a saida faz com o afinador ligado. */
@Composable
internal fun TunerSettings(pedal: PedalState, onGlobalSettingChange: (GlobalSetting, Float) -> Unit) {
    val reference = GlobalSetting.TUNER_REFERENCE
    StepperSetting(
        titleRes = R.string.menu_a4_reference,
        noteRes = R.string.menu_a4_note,
        value = pedal.a4Reference,
        range = reference.min.toInt()..reference.max.toInt(),
        unit = "Hz",
        onChange = { onGlobalSettingChange(reference, it.toFloat()) }
    )
    ChoiceSetting(
        titleRes = R.string.menu_tuner_output,
        noteRes = R.string.menu_tuner_hardware_note,
        options = listOf(R.string.menu_tuner_output_mute, R.string.menu_tuner_output_thru),
        selectedIndex = if (pedal.tunerThru) 1 else 0,
        onSelect = { onGlobalSettingChange(GlobalSetting.TUNER_OUTPUT, it.toFloat()) }
    )
}

/** BPM global e se os presets usam o proprio BPM. */
@Composable
internal fun TempoSettings(pedal: PedalState, onGlobalSettingChange: (GlobalSetting, Float) -> Unit) {
    val tempo = GlobalSetting.TEMPO
    StepperSetting(
        titleRes = R.string.menu_tempo,
        noteRes = null,
        value = pedal.tempo,
        range = tempo.min.toInt()..tempo.max.toInt(),
        unit = "BPM",
        onChange = { onGlobalSettingChange(tempo, it.toFloat()) }
    )
    ChoiceSetting(
        titleRes = R.string.menu_tempo_source,
        noteRes = R.string.menu_tempo_note,
        options = listOf(R.string.menu_tempo_global, R.string.menu_tempo_preset),
        selectedIndex = if (pedal.tempoFromPreset) 1 else 0,
        onSelect = { onGlobalSettingChange(GlobalSetting.TEMPO_SOURCE, it.toFloat()) }
    )
}

@Composable
private fun SettingCard(@StringRes titleRes: Int, @StringRes noteRes: Int?, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            content()
            if (noteRes != null) {
                Text(
                    text = stringResource(noteRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * O valor arrastado fica local ate o dedo soltar: so entao vai ao pedal, ja encaixado em
 * [step]. Assim o poll do estado nao puxa o controle de volta no meio do gesto, e o pedal
 * recebe uma escrita so.
 */
@Composable
private fun SliderSetting(
    @StringRes titleRes: Int,
    @StringRes noteRes: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var dragged by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    SettingCard(titleRes, noteRes) {
        Text(text = format(snapToStep(dragged, step)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onCommit(snapToStep(dragged, step)) },
            valueRange = range
        )
    }
}

@Composable
private fun StepperSetting(
    @StringRes titleRes: Int,
    @StringRes noteRes: Int?,
    value: Int,
    range: IntRange,
    unit: String,
    onChange: (Int) -> Unit
) {
    SettingCard(titleRes, noteRes) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.menu_decrease))
            }
            Text(text = "$value $unit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_increase))
            }
        }
    }
}

@Composable
private fun ChoiceSetting(
    @StringRes titleRes: Int,
    @StringRes noteRes: Int?,
    options: List<Int>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    SettingCard(titleRes, noteRes) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, labelRes ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { if (index != selectedIndex) onSelect(index) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}
