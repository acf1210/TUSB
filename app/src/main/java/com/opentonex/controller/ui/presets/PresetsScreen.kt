package com.opentonex.controller.ui.presets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.opentonex.controller.ui.components.EffectChip
import com.opentonex.controller.ui.components.TusbSegmentedRow
import com.opentonex.controller.ui.components.tusbBackground
import com.opentonex.controller.ui.theme.ChipAmp
import com.opentonex.controller.ui.theme.ChipCab
import com.opentonex.controller.ui.theme.ChipCmp
import com.opentonex.controller.ui.theme.ChipDly
import com.opentonex.controller.ui.theme.ChipMod
import com.opentonex.controller.ui.theme.ChipNg
import com.opentonex.controller.ui.theme.ChipRev
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXBackground
import com.opentonex.controller.ui.theme.ToneXGreen
import com.opentonex.controller.ui.theme.ToneXOnSurfaceFaint
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSlotA
import com.opentonex.controller.ui.theme.ToneXSlotB
import com.opentonex.controller.ui.theme.ToneXSlotC
import com.opentonex.controller.ui.theme.ToneXSlotCyan
import com.opentonex.controller.ui.theme.ToneXSurfaceHigh
import com.opentonex.controller.ui.theme.ToneXSurfaceVariant

@Composable
fun PresetsScreen(
    activeSlot: Slot,
    pedalMode: PedalMode,
    bypassMode: Boolean,
    presets: List<PresetSlot>,
    libraryPresets: List<LibraryPreset>,
    isBusy: Boolean,
    /** Rotulo do cab do preset ativo (ex.: "VIR 4"), derivado do detalhe 0x0304. */
    activeCabLabel: String? = null,
    /** false quando o bloco de amp (Tone Model) do preset ativo esta' desligado. */
    activeAmpEnabled: Boolean? = null,
    /** Apelidos e nomes manuais de amp/cab por preset (persistidos localmente). */
    customizations: Map<Int, PresetCustomization> = emptyMap(),
    onSaveCustomization: ((Int, PresetCustomization) -> Unit)? = null,
    onSelectSlot: (Slot) -> Unit,
    onLoadPreset: (Int) -> Unit,
    onSwitchMode: (PedalMode) -> Unit,
    onToggleBypass: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingPreset by remember { mutableStateOf<LibraryPreset?>(null) }
    val visiblePresets = remember(libraryPresets, searchQuery, customizations) {
        val source = libraryPresets.ifEmpty {
            (0 until 20).map { index -> LibraryPreset(index = index, name = "Preset ${index + 1}", color = fallbackPresetColor(index)) }
        }
        if (searchQuery.isBlank()) source
        else source.filter { preset ->
            val display = customizations[preset.index]?.name ?: preset.name
            display.contains(searchQuery, ignoreCase = true) ||
                preset.name.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().tusbBackground(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(modifier = Modifier.widthIn(max = 620.dp).fillMaxSize()) {
        // Seletor de modo (segmentado, estilo tabs do design)
        TusbSegmentedRow(
            options = listOf("Dual Mode", "Stomp Mode"),
            selectedIndex = if (pedalMode == PedalMode.AB) 0 else 1,
            activeColor = ToneXSurfaceHigh,
            activeTextColor = Color.White,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            onSelect = { index -> onSwitchMode(if (index == 0) PedalMode.AB else PedalMode.STOMP) }
        )

        // Deck de footswitches A/B/C (+ bypass no modo Dual)
        FootswitchDeck(
            activeSlot = activeSlot,
            pedalMode = pedalMode,
            bypassMode = bypassMode,
            presets = presets,
            isBusy = isBusy,
            onSelectSlot = onSelectSlot,
            onToggleBypass = onToggleBypass
        )

        // Busca
        SearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Lista de presets da biblioteca no estilo do design
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visiblePresets, key = { it.index }) { preset ->
                val slotBadge = slotForPreset(preset, presets)
                val isActive = slotBadge != null && slotBadge == activeSlot
                val custom = customizations[preset.index]
                PresetListRow(
                    number = preset.index + 1,
                    name = custom?.name ?: preset.name,
                    accent = preset.color.toColor(),
                    slotBadge = slotBadge,
                    isActive = isActive,
                    // Nomes manuais tem prioridade; sem eles, o preset ATIVO usa o 0x0304
                    rigLine = manualRigLine(custom)
                        ?: if (isActive) rigLine(activeAmpEnabled, activeCabLabel) else null,
                    enabled = !isBusy,
                    onClick = { onLoadPreset(preset.index) },
                    onLongClick = if (onSaveCustomization != null) {
                        { editingPreset = preset }
                    } else null
                )
            }
        }
    }
    }

    val editing = editingPreset
    if (editing != null && onSaveCustomization != null) {
        PresetEditDialog(
            preset = editing,
            current = customizations[editing.index] ?: PresetCustomization(),
            onDismiss = { editingPreset = null },
            onSave = { customization ->
                onSaveCustomization(editing.index, customization)
                editingPreset = null
            }
        )
    }
}

/** Linha de rig com nomes manuais ("AMP JCM800 · CAB Brit 4x12"); null se nada informado. */
private fun manualRigLine(custom: PresetCustomization?): String? {
    if (custom == null || (custom.ampName.isNullOrBlank() && custom.cabName.isNullOrBlank())) return null
    return listOfNotNull(
        custom.ampName?.takeIf { it.isNotBlank() }?.let { "AMP $it" },
        custom.cabName?.takeIf { it.isNotBlank() }?.let { "CAB $it" }
    ).joinToString(" · ")
}

/** Se o preset da biblioteca esta' carregado em um slot A/B/C (match por nome). */
private fun slotForPreset(preset: LibraryPreset, slots: List<PresetSlot>): Slot? {
    val index = slots.indexOfFirst { it.name == preset.name }
    return if (index >= 0) Slot.entries.getOrNull(index) else null
}

/** Linha "AMP · CAB VIR 4" do preset ativo; null se o detalhe ainda nao chegou. */
private fun rigLine(ampEnabled: Boolean?, cabLabel: String?): String? {
    if (ampEnabled == null && cabLabel == null) return null
    val amp = if (ampEnabled == false) "AMP OFF" else "AMP"
    val cab = cabLabel?.let { "CAB $it" }
    return listOfNotNull(amp, cab).joinToString(" · ")
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = ToneXSurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = ToneXOnSurfaceFaint,
                modifier = Modifier.size(16.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(text = stringResource(R.string.presets_search), fontSize = 14.sp, color = ToneXOnSurfaceFaint)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                    cursorBrush = SolidColor(ToneXAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Linha de preset do design: borda lateral colorida, estrela, numero mono + nome,
 * chips de blocos e badge circular do slot quando carregado no pedal.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PresetListRow(
    number: Int,
    name: String,
    accent: Color,
    slotBadge: Slot?,
    isActive: Boolean,
    rigLine: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(if (isActive) ToneXSurfaceVariant else Color.Transparent)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(accent))
        Row(
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 13.dp, bottom = 13.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (isActive) ToneXAccent else ToneXOnSurfaceFaint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = number.toString().padStart(2, '0'),
                        style = MonoLabelStyle.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                        color = ToneXOnSurfaceMuted
                    )
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (slotBadge != null) {
                        SlotBadge(slot = slotBadge)
                    }
                }
                if (rigLine != null) {
                    Text(
                        text = rigLine,
                        style = MonoLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                        color = ToneXOnSurfaceMuted
                    )
                }
                EffectChipsRow()
            }
        }
    }
}

/**
 * Dialogo de personalizacao do preset (toque longo na linha): apelido + nomes manuais
 * de amp e cab. Os dados ficam SO no aparelho — o protocolo USB nao permite renomear
 * presets nem le os nomes dos captures.
 */
@Composable
private fun PresetEditDialog(
    preset: LibraryPreset,
    current: PresetCustomization,
    onDismiss: () -> Unit,
    onSave: (PresetCustomization) -> Unit
) {
    var name by remember(preset.index) { mutableStateOf(current.name ?: preset.name) }
    var amp by remember(preset.index) { mutableStateOf(current.ampName.orEmpty()) }
    var cab by remember(preset.index) { mutableStateOf(current.cabName.orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ToneXSurfaceVariant,
        title = {
            Text(
                text = "${(preset.index + 1).toString().padStart(2, '0')} · ${preset.name}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_edit_name)) },
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = amp,
                    onValueChange = { amp = it },
                    label = { Text(stringResource(R.string.preset_edit_amp)) },
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = cab,
                    onValueChange = { cab = it },
                    label = { Text(stringResource(R.string.preset_edit_cab)) },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.preset_edit_hint),
                    fontSize = 11.sp,
                    color = ToneXOnSurfaceMuted
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(
                    PresetCustomization(
                        name = name.takeIf { it.isNotBlank() && it != preset.name },
                        ampName = amp.takeIf { it.isNotBlank() },
                        cabName = cab.takeIf { it.isNotBlank() }
                    )
                )
            }) { Text(stringResource(R.string.preset_edit_save), color = ToneXAccent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.preset_edit_cancel), color = ToneXOnSurfaceMuted)
            }
        }
    )
}

@Composable
private fun SlotBadge(slot: Slot) {
    val color = slotColor(slot)
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slot.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (slot == Slot.B) Color.White else Color(0xFF111111)
        )
    }
}

/**
 * Chips de blocos de efeito com as cores do design. Estado generico: ainda nao
 * decodificamos quais blocos cada preset da biblioteca usa (preset detail 0x0304).
 */
@Composable
private fun EffectChipsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EffectChip("NG", ChipNg)
        EffectChip("AMP", ChipAmp)
        EffectChip("CAB", ChipCab)
        EffectChip("CMP", ChipCmp)
        EffectChip("MOD", ChipMod)
        EffectChip("DLY", ChipDly)
        EffectChip("REV", ChipRev)
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
}

@Composable
private fun FootswitchButton(slot: Slot, preset: PresetSlot?, isActive: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = preset?.color?.toColor() ?: slotColor(slot)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) color.copy(alpha = 0.16f) else ToneXSurfaceVariant)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) color else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = slot.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(color))
        Text(
            text = preset?.name ?: "Preset ${slot.name}",
            fontSize = 10.sp,
            color = ToneXOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BypassButton(bypassMode: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (bypassMode) MaterialTheme.colorScheme.error else ToneXGreen
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (bypassMode) color.copy(alpha = 0.16f) else ToneXSurfaceVariant)
            .border(1.dp, if (bypassMode) color else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
        Text(
            text = if (bypassMode) "BYPASS" else "ACTIVE",
            style = MonoLabelStyle,
            color = if (bypassMode) color else ToneXOnSurfaceMuted
        )
    }
}

private fun slotColor(slot: Slot): Color = when (slot) {
    Slot.A -> ToneXSlotA
    Slot.B -> ToneXSlotB
    Slot.C -> ToneXSlotC
}

private fun fallbackPresetColor(index: Int): Rgb = when (index % 5) {
    0 -> Rgb(240, 160, 48)
    1 -> Rgb(112, 64, 208)
    2 -> Rgb(76, 217, 100)
    3 -> Rgb(0, 206, 200)
    else -> Rgb(56, 96, 180)
}

private fun Rgb.toColor(): Color = Color(r, g, b)
