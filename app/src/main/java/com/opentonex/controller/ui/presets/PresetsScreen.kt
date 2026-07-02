package com.opentonex.controller.ui.presets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.domain.LibraryPreset
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

private val PanelShape = RoundedCornerShape(8.dp)
private val FootswitchShape = RoundedCornerShape(6.dp)

@Composable
fun PresetsScreen(
    activeSlot: Slot,
    pedalMode: PedalMode,
    bypassMode: Boolean,
    presets: List<PresetSlot>,
    libraryPresets: List<LibraryPreset>,
    isBusy: Boolean,
    onSelectSlot: (Slot) -> Unit,
    onLoadPreset: (Int) -> Unit,
    onSwitchMode: (PedalMode) -> Unit,
    onToggleBypass: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ModeSelector(pedalMode = pedalMode, isBusy = isBusy, onSwitchMode = onSwitchMode) }
        item {
            FootswitchDeck(
                activeSlot = activeSlot,
                pedalMode = pedalMode,
                bypassMode = bypassMode,
                presets = presets,
                isBusy = isBusy,
                onSelectSlot = onSelectSlot,
                onToggleBypass = onToggleBypass
            )
        }
        item { PresetLibrary(presets = libraryPresets, isBusy = isBusy, onLoadPreset = onLoadPreset) }
        item {
            Text(text = "Slots do pedal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(presets, key = { it.index }) { preset ->
            PresetRow(
                preset = preset,
                isActive = preset.index == activeSlot.ordinal,
                enabled = !isBusy,
                onClick = { onSelectSlot(Slot.entries[preset.index]) }
            )
        }
    }
}

@Composable
private fun ModeSelector(pedalMode: PedalMode, isBusy: Boolean, onSwitchMode: (PedalMode) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = PanelShape) {
        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeTab(text = "Dual Mode", selected = pedalMode == PedalMode.AB, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                onSwitchMode(PedalMode.AB)
            }
            ModeTab(text = "Stomp Mode", selected = pedalMode == PedalMode.STOMP, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                onSwitchMode(PedalMode.STOMP)
            }
        }
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FootswitchDeck(
    activeSlot: Slot,
    pedalMode: PedalMode,
    bypassMode: Boolean,
    presets: List<PresetSlot>,
    isBusy: Boolean,
    onSelectSlot: (Slot) -> Unit,
    onToggleBypass: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = PanelShape) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val visibleSlots = if (pedalMode == PedalMode.STOMP) Slot.entries else listOf(Slot.A, Slot.B)
                visibleSlots.forEach { slot ->
                    val preset = presets.getOrNull(slot.ordinal)
                    FootswitchButton(
                        slot = slot,
                        preset = preset,
                        isActive = slot == activeSlot,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectSlot(slot) }
                    )
                }
                if (pedalMode == PedalMode.AB) {
                    BypassButton(bypassMode = bypassMode, enabled = !isBusy, modifier = Modifier.weight(1f), onClick = onToggleBypass)
                }
            }
            Text(
                text = if (pedalMode == PedalMode.STOMP) {
                    "Modo STOMP: A, B e C ficam disponíveis como cenas no pedal."
                } else {
                    "Modo A/B: A e B alternam presets; o terceiro switch controla bypass."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FootswitchButton(slot: Slot, preset: PresetSlot?, isActive: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = preset?.color?.toColor() ?: slotColor(slot)
    Surface(
        modifier = modifier.aspectRatio(0.86f).clickable(enabled = enabled, onClick = onClick),
        color = if (isActive) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        shape = FootswitchShape,
        tonalElevation = if (isActive) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = FootswitchShape
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = slot.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(color))
            Text(text = preset?.name ?: "Preset ${slot.name}", style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BypassButton(bypassMode: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.aspectRatio(0.86f).clickable(enabled = enabled, onClick = onClick),
        color = if (bypassMode) MaterialTheme.colorScheme.error.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surface,
        shape = FootswitchShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, if (bypassMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), FootswitchShape)
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(30.dp), tint = if (bypassMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(text = if (bypassMode) "BYPASS" else "ACTIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PresetLibrary(presets: List<LibraryPreset>, isBusy: Boolean, onLoadPreset: (Int) -> Unit) {
    val visiblePresets = if (presets.isNotEmpty()) {
        presets
    } else {
        (0 until 20).map { index -> LibraryPreset(index = index, name = "Preset ${index + 1}", color = fallbackPresetColor(index)) }
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = PanelShape) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "20 presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = "carrega no slot ativo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyRow(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visiblePresets, key = { it.index }) { preset ->
                    LibraryPresetButton(preset = preset, enabled = !isBusy, onClick = { onLoadPreset(preset.index) })
                }
            }
        }
    }
}

@Composable
private fun LibraryPresetButton(preset: LibraryPreset, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(246.dp).height(90.dp).clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = FootswitchShape
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(66.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0B0B0C))
                    .border(1.dp, preset.color.toColor().copy(alpha = 0.62f), RoundedCornerShape(4.dp))
            ) {
                Image(
                    painter = painterResource(presetSkinRes(preset.index)),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    contentScale = ContentScale.Fit
                )
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(9.dp).clip(CircleShape).background(preset.color.toColor()))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${(preset.index + 1).toString().padStart(2, '0')}  ${bankLabel(preset.index)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = preset.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EffectChipsRow()
            }
        }
    }
}

/**
 * Chips de blocos de efeito (AMP/CAB/CMP/MOD/DLY/REV) por preset, como na lista do app oficial.
 * Estado neutro/generico: ainda nao sabemos quais blocos cada preset usa de verdade (falta
 * decodificar o preset detail 0x0304 por completo), entao os chips nao refletem dados reais.
 */
@Composable
private fun EffectChipsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        PRESET_EFFECT_TAGS.forEach { tag ->
            Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(3.dp)) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private val PRESET_EFFECT_TAGS = listOf("AMP", "CAB", "CMP", "MOD", "DLY", "REV")

@Composable
private fun PresetRow(preset: PresetSlot, isActive: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surface,
        shape = PanelShape
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorSwatch(preset.color)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "Slot ${Slot.entries[preset.index].name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isActive) {
                Text(text = "ativo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Rgb) {
    Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(color.toColor()))
}

private fun slotColor(slot: Slot): Color = when (slot) {
    Slot.A -> Color(0xFFE74C3C)
    Slot.B -> Color(0xFF2ECC71)
    Slot.C -> Color(0xFF3498DB)
}

private fun fallbackPresetColor(index: Int): Rgb = when (index % 5) {
    0 -> Rgb(231, 76, 60)
    1 -> Rgb(46, 204, 113)
    2 -> Rgb(52, 152, 219)
    3 -> Rgb(245, 176, 65)
    else -> Rgb(155, 89, 182)
}

private fun bankLabel(index: Int): String {
    val bank = 'A' + (index / 2)
    val side = if (index % 2 == 0) "A" else "B"
    return "$bank$side"
}

private fun presetSkinRes(index: Int): Int = when (index % 6) {
    0 -> R.drawable.fndrtwin
    1 -> R.drawable.tnxablk
    2 -> R.drawable.ibnzgrn
    3 -> R.drawable.mxrsnggd
    4 -> R.drawable.bossyel
    else -> R.drawable.slvrface
}

private fun Rgb.toColor(): Color = Color(r, g, b)
