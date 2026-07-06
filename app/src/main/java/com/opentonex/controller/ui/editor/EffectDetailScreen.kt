package com.opentonex.controller.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.ui.EffectControl
import com.opentonex.controller.ui.EffectDetailUiState
import com.opentonex.controller.ui.components.KnobSize
import com.opentonex.controller.ui.components.TusbKnob
import com.opentonex.controller.ui.components.TusbSegmentedRow
import com.opentonex.controller.ui.components.tusbBackground
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXBackground
import com.opentonex.controller.ui.theme.ToneXDivider
import com.opentonex.controller.ui.theme.ToneXGreen
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurfaceHigh

/**
 * Editor de um bloco de efeito no layout do design TUSB (tela "Effect Detail"):
 * cabecalho com o nome do bloco, seletor de modo segmentado, knobs verdes de 300
 * graus e segmentos PRE/POST + SYNC. Os controles escrevem no pedal em tempo real
 * via comando de parametro unico 0x0309.
 */
@Composable
fun EffectDetailScreen(
    effect: EffectSlotType,
    enabled: Boolean,
    detail: EffectDetailUiState,
    onToggleEnabled: () -> Unit,
    onControlChange: (EffectControl, Float) -> Unit,
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

    Box(
        modifier = modifier.fillMaxSize().tusbBackground(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(modifier = Modifier.widthIn(max = 620.dp).fillMaxSize()) {
        // Cabecalho: voltar + nome do bloco + switch de ativo
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.effect_back),
                    tint = ToneXOnSurfaceMuted
                )
            }
            Text(
                text = effect.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = effect.fullName,
                fontSize = 18.sp,
                color = ToneXOnSurfaceMuted,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            Switch(
                checked = enabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ToneXGreen,
                    checkedThumbColor = Color(0xFF111111)
                )
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ToneXDivider))

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Seletor de modo do delay: NORMAL / PING PONG
            if (effect == EffectSlotType.DLY) {
                TusbSegmentedRow(
                    options = listOf("Normal", "Ping Pong"),
                    selectedIndex = if (normalMode) 0 else 1,
                    activeColor = ToneXGreen,
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { index ->
                        normalMode = index == 0
                        onControlChange(EffectControl.DELAY_PINGPONG, if (index == 0) 0f else 1f)
                    }
                )
            }
            val cabinetType = detail.cabinetType
            if (effect == EffectSlotType.CAB && cabinetType != null) {
                TusbSegmentedRow(
                    options = listOf("Tone Model", "VIR", "Off"),
                    selectedIndex = cabinetType,
                    activeColor = ToneXGreen,
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { index ->
                        onControlChange(EffectControl.CABINET_TYPE, index.toFloat())
                    }
                )
            }

            // Knobs verdes (acento do design para blocos de efeito)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TusbKnob(
                    label = labelA,
                    value = knobA,
                    valueText = String.format("%.1f", knobA * 10f),
                    size = KnobSize.MEDIUM,
                    accent = ToneXGreen,
                    onValueChange = {
                        knobA = it
                        onControlChange(EffectControl.KNOB_A, it)
                    }
                )
                TusbKnob(
                    label = labelB,
                    value = knobB,
                    valueText = String.format("%.1f", knobB * 10f),
                    size = KnobSize.MEDIUM,
                    accent = ToneXGreen,
                    onValueChange = {
                        knobB = it
                        onControlChange(EffectControl.KNOB_B, it)
                    }
                )
                TusbKnob(
                    label = labelC,
                    value = knobC,
                    valueText = String.format("%.1f", knobC * 10f),
                    size = KnobSize.MEDIUM,
                    accent = ToneXGreen,
                    onValueChange = {
                        knobC = it
                        onControlChange(EffectControl.KNOB_C, it)
                    }
                )
            }

            // PRE/POST + SYNC
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TusbSegmentedRow(
                    options = listOf("Pre", "Post"),
                    selectedIndex = if (postPosition) 1 else 0,
                    activeColor = ToneXGreen,
                    modifier = Modifier.weight(1f),
                    onSelect = { index ->
                        postPosition = index == 1
                        onControlChange(EffectControl.POST, if (index == 1) 1f else 0f)
                    }
                )
                if (effect == EffectSlotType.DLY) {
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .clickable {
                                sync = !sync
                                onControlChange(EffectControl.DELAY_SYNC, if (sync) 1f else 0f)
                            }
                            .background(
                                color = if (sync) ToneXGreen else ToneXSurfaceHigh,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SYNC",
                            style = MonoLabelStyle.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (sync) Color(0xFF111111) else ToneXOnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
    }
}
