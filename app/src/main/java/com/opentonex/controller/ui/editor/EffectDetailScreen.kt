package com.opentonex.controller.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentonex.controller.R

/**
 * Editor de um bloco de efeito. Os controles escrevem no pedal em tempo real via comando
 * de parametro unico 0x0309 (ver TonexEffectParams); os valores iniciais vem do bloco de
 * parametros do preset ativo (detalhe 0x0304).
 */
@Composable
fun EffectDetailScreen(
    effect: EffectSlotType,
    enabled: Boolean,
    detail: com.opentonex.controller.ui.EffectDetailUiState,
    onToggleEnabled: () -> Unit,
    onControlChange: (com.opentonex.controller.ui.EffectControl, Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var normalMode by remember(effect) { mutableStateOf(!detail.delayPingPong) }
    var postPosition by remember(effect) { mutableStateOf(detail.post) }
    var sync by remember(effect) { mutableStateOf(detail.delaySync) }
    var knobA by remember(effect) { mutableFloatStateOf(detail.knobA) }
    var knobB by remember(effect) { mutableFloatStateOf(detail.knobB) }
    var knobC by remember(effect) { mutableFloatStateOf(detail.knobC) }

    val (labelA, labelB, labelC) = when (effect) {
        EffectSlotType.GATE -> Triple("THRESHOLD", "DEPTH", "RELEASE")
        EffectSlotType.CMP -> Triple("ATTACK", "THRESHOLD", "MAKEUP")
        EffectSlotType.EQ -> Triple("BASS FREQ", "MID FREQ", "TREBLE FREQ")
        EffectSlotType.MOD -> Triple("RATE", "DEPTH", "MIX")
        EffectSlotType.DLY -> Triple("TIME", "FEEDBACK", "MIX")
        EffectSlotType.REV -> Triple("MIX", "TIME", "PREDELAY")
        EffectSlotType.CAB -> Triple("MIC BLEND", "RESONANCE", "MIC POSITION")
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.effect_back))
            }
            Text(text = effect.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.effect_block_active), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { onToggleEnabled() })
            }
        }

        if (effect == EffectSlotType.DLY) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = normalMode,
                    onClick = {
                        normalMode = true
                        onControlChange(com.opentonex.controller.ui.EffectControl.DELAY_PINGPONG, 0f)
                    },
                    label = { Text("NORMAL") }
                )
                FilterChip(
                    selected = !normalMode,
                    onClick = {
                        normalMode = false
                        onControlChange(com.opentonex.controller.ui.EffectControl.DELAY_PINGPONG, 1f)
                    },
                    label = { Text("PING PONG") }
                )
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EffectSliderRow(label = labelA, value = knobA, onValueChange = {
                    knobA = it
                    onControlChange(com.opentonex.controller.ui.EffectControl.KNOB_A, it)
                })
                EffectSliderRow(label = labelB, value = knobB, onValueChange = {
                    knobB = it
                    onControlChange(com.opentonex.controller.ui.EffectControl.KNOB_B, it)
                })
                EffectSliderRow(label = labelC, value = knobC, onValueChange = {
                    knobC = it
                    onControlChange(com.opentonex.controller.ui.EffectControl.KNOB_C, it)
                })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            FilterChip(
                selected = !postPosition,
                onClick = {
                    postPosition = false
                    onControlChange(com.opentonex.controller.ui.EffectControl.POST, 0f)
                },
                label = { Text("PRE") }
            )
            FilterChip(
                selected = postPosition,
                onClick = {
                    postPosition = true
                    onControlChange(com.opentonex.controller.ui.EffectControl.POST, 1f)
                },
                label = { Text("POST") }
            )
            if (effect == EffectSlotType.DLY) {
                FilterChip(
                    selected = sync,
                    onClick = {
                        sync = !sync
                        onControlChange(com.opentonex.controller.ui.EffectControl.DELAY_SYNC, if (sync) 1f else 0f)
                    },
                    label = { Text("SYNC") }
                )
            }
        }

        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.effect_back_to_editor))
        }
    }
}

@Composable
private fun EffectSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text = "${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange)
    }
}
