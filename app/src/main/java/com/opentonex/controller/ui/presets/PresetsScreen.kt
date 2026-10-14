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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

private enum class PresetListMode { DUAL, STOMP, BANK }

@Composable
fun PresetsScreen(
    activeSlot: Slot,
    pedalMode: PedalMode,
    bypassMode: Boolean,
    presets: List<PresetSlot>,
    /** Ids da biblioteca atribuídos aos slots, na ordem A, B, C. */
    presetIds: List<Int> = emptyList(),
    libraryPresets: List<LibraryPreset>,
    banks: List<PresetBank> = emptyList(),
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
    onApplyBank: (PresetBank) -> Unit = {},
    onSaveBanks: (List<PresetBank>) -> Unit = {},
    onSwitchMode: (PedalMode) -> Unit,
    onToggleBypass: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingPreset by remember { mutableStateOf<LibraryPreset?>(null) }
    var listMode by remember { mutableStateOf(if (pedalMode == PedalMode.STOMP) PresetListMode.STOMP else PresetListMode.DUAL) }
    var editingBank by remember { mutableStateOf<BankDraft?>(null) }
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
        TusbSegmentedRow(
            options = listOf(
                stringResource(R.string.presets_mode_dual),
                stringResource(R.string.presets_mode_stomp),
                stringResource(R.string.presets_mode_bank)
            ),
            selectedIndex = when (listMode) {
                PresetListMode.DUAL -> 0
                PresetListMode.STOMP -> 1
                PresetListMode.BANK -> 2
            },
            activeColor = ToneXSurfaceHigh,
            activeTextColor = Color.White,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            onSelect = { index ->
                when (index) {
                    0 -> {
                        listMode = PresetListMode.DUAL
                        if (pedalMode != PedalMode.AB) onSwitchMode(PedalMode.AB)
                    }
                    1 -> {
                        listMode = PresetListMode.STOMP
                        if (pedalMode != PedalMode.STOMP) onSwitchMode(PedalMode.STOMP)
                    }
                    else -> {
                        listMode = PresetListMode.BANK
                        if (pedalMode != PedalMode.AB) onSwitchMode(PedalMode.AB)
                    }
                }
            }
        )

        if (listMode == PresetListMode.BANK) {
            BankSlotDeck(
                presetIds = presetIds,
                libraryPresets = libraryPresets,
                customizations = customizations,
                activeSlot = activeSlot,
                isBusy = isBusy,
                onSelectSlot = onSelectSlot
            )
        } else {
            FootswitchDeck(
                activeSlot = activeSlot,
                pedalMode = if (listMode == PresetListMode.STOMP) PedalMode.STOMP else PedalMode.AB,
                bypassMode = bypassMode,
                presets = presets,
                isBusy = isBusy,
                onSelectSlot = onSelectSlot,
                onToggleBypass = onToggleBypass
            )
        }

        SearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (listMode == PresetListMode.BANK) {
            val visibleBanks = banks.mapIndexed { index, bank -> index to bank }.filter { (index, bank) ->
                if (searchQuery.isBlank()) true
                else {
                    val id = bankNumber(index)
                    bank.name.contains(searchQuery, ignoreCase = true) || id.contains(searchQuery)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "add-bank") {
                    TextButton(
                        onClick = {
                            editingBank = BankDraft(
                                index = null,
                                name = "Bank ${bankNumber(banks.size)}",
                                presetA = presetIds.getOrElse(0) { 0 }.coerceIn(0, 19),
                                presetB = presetIds.getOrElse(1) { 1 }.coerceIn(0, 19)
                            )
                        },
                        enabled = !isBusy && banks.size < PresetBankCodec.MAX_BANKS
                    ) {
                        Text(stringResource(R.string.bank_add), color = ToneXAccent)
                    }
                }
                if (banks.isEmpty()) {
                    item(key = "banks-empty") {
                        Text(
                            text = stringResource(R.string.bank_empty),
                            color = ToneXOnSurfaceMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
                items(visibleBanks, key = { (index, _) -> "bank-$index" }) { (index, bank) ->
                    val loaded = presetIds.getOrNull(0) == bank.presetA && presetIds.getOrNull(1) == bank.presetB
                    BankListRow(
                        number = bankNumber(index),
                        name = bank.name,
                        presetAName = presetDisplayName(bank.presetA, libraryPresets, customizations),
                        presetBName = presetDisplayName(bank.presetB, libraryPresets, customizations),
                        colorA = presetColor(bank.presetA, libraryPresets),
                        colorB = presetColor(bank.presetB, libraryPresets),
                        isLoaded = loaded,
                        enabled = !isBusy,
                        onClick = { onApplyBank(bank) },
                        onLongClick = {
                            editingBank = BankDraft(index, bank.name, bank.presetA, bank.presetB)
                        }
                    )
                }
            }
        } else {
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

    val draft = editingBank
    if (draft != null) {
        BankEditDialog(
            draft = draft,
            libraryPresets = libraryPresets,
            customizations = customizations,
            onDismiss = { editingBank = null },
            onSave = { name, presetA, presetB ->
                val updated = PresetBank(name = name, presetA = presetA, presetB = presetB)
                onSaveBanks(
                    if (draft.index == null) banks + updated
                    else banks.mapIndexed { index, bank -> if (index == draft.index) updated else bank }
                )
                editingBank = null
            },
            onDelete = if (draft.index != null) {
                {
                    onSaveBanks(banks.filterIndexed { index, _ -> index != draft.index })
                    editingBank = null
                }
            } else null
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
private fun EffectChipsRow(prefix: String? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix != null) {
            Text(
                text = prefix,
                style = MonoLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                color = ToneXOnSurfaceMuted
            )
        }
        EffectChip("NG", ChipNg)
        EffectChip("AMP", ChipAmp)
        EffectChip("CAB", ChipCab)
        EffectChip("CMP", ChipCmp)
        EffectChip("MOD", ChipMod)
        EffectChip("DLY", ChipDly)
        EffectChip("REV", ChipRev)
    }
}

private data class BankDraft(
    val index: Int?,
    val name: String,
    val presetA: Int,
    val presetB: Int
)

private fun bankNumber(index: Int): String = (index + 1).toString().padStart(2, '0')

private fun presetColor(presetId: Int, libraryPresets: List<LibraryPreset>): Color =
    libraryPresets.getOrNull(presetId)?.color?.toColor() ?: fallbackPresetColor(presetId).toColor()

private fun presetDisplayName(
    presetId: Int,
    libraryPresets: List<LibraryPreset>,
    customizations: Map<Int, PresetCustomization>
): String {
    customizations[presetId]?.name?.takeIf { it.isNotBlank() }?.let { return it }
    return libraryPresets.getOrNull(presetId)?.name ?: "Preset ${presetId + 1}"
}

@Composable
private fun BankSlotDeck(
    presetIds: List<Int>,
    libraryPresets: List<LibraryPreset>,
    customizations: Map<Int, PresetCustomization>,
    activeSlot: Slot,
    isBusy: Boolean,
    onSelectSlot: (Slot) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(Slot.A, Slot.B).forEach { slot ->
            val presetId = presetIds.getOrNull(slot.ordinal) ?: 0
            BankSlotButton(
                letter = slot.name,
                name = presetDisplayName(presetId, libraryPresets, customizations),
                color = presetColor(presetId, libraryPresets),
                isActive = slot == activeSlot,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                onClick = { onSelectSlot(slot) }
            )
        }
    }
}

@Composable
private fun BankSlotButton(
    letter: String,
    name: String,
    color: Color,
    isActive: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
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
        Text(text = letter, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(color))
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BankListRow(
    number: String,
    name: String,
    presetAName: String,
    presetBName: String,
    colorA: Color,
    colorB: Color,
    isLoaded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(if (isLoaded) ToneXSurfaceVariant else Color.Transparent)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        0f to colorA,
                        0.5f to colorA,
                        0.5f to colorB,
                        1f to colorB
                    )
                )
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = number,
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
            }
            Text(
                text = presetAName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            EffectChipsRow(prefix = "A:")
            Text(
                text = presetBName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            EffectChipsRow(prefix = "B:")
        }
    }
}

@Composable
private fun BankEditDialog(
    draft: BankDraft,
    libraryPresets: List<LibraryPreset>,
    customizations: Map<Int, PresetCustomization>,
    onDismiss: () -> Unit,
    onSave: (name: String, presetA: Int, presetB: Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(draft.index, draft.name) { mutableStateOf(draft.name) }
    var presetA by remember(draft.index, draft.presetA) { mutableStateOf(draft.presetA) }
    var presetB by remember(draft.index, draft.presetB) { mutableStateOf(draft.presetB) }
    val options = (0 until 20).map { index ->
        index to presetDisplayName(index, libraryPresets, customizations)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ToneXSurfaceVariant,
        title = {
            Text(
                text = stringResource(if (draft.index == null) R.string.bank_new_title else R.string.bank_edit_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.bank_name)) },
                    singleLine = true
                )
                PresetDropdown(
                    label = stringResource(R.string.bank_preset_a),
                    selected = presetA,
                    options = options,
                    onSelect = { presetA = it }
                )
                PresetDropdown(
                    label = stringResource(R.string.bank_preset_b),
                    selected = presetB,
                    options = options,
                    onSelect = { presetB = it }
                )
                Text(
                    text = stringResource(R.string.bank_edit_hint),
                    fontSize = 11.sp,
                    color = ToneXOnSurfaceMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name.trim().ifEmpty { "Bank" }, presetA, presetB)
            }) { Text(stringResource(R.string.preset_edit_save), color = ToneXAccent) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.bank_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.preset_edit_cancel), color = ToneXOnSurfaceMuted)
                }
            }
        }
    )
}

@Composable
private fun PresetDropdown(
    label: String,
    selected: Int,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.let { (index, presetName) ->
        "${bankNumber(index)}  $presetName"
    }.orEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (index, presetName) ->
                DropdownMenuItem(
                    text = { Text("${bankNumber(index)}  $presetName") },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
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
