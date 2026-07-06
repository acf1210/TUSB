package com.opentonex.controller.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.RigModels
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.AmpKnobUiState
import com.opentonex.controller.ui.EffectChainUiState
import com.opentonex.controller.ui.components.KnobSize
import com.opentonex.controller.ui.components.TusbKnob
import com.opentonex.controller.ui.components.tusbBackground
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXBackground
import com.opentonex.controller.ui.theme.ToneXDivider
import com.opentonex.controller.ui.theme.ToneXGreen
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurfaceHigh

/** Blocos de efeito exibidos na esteira do Editor, na ordem tipica da cadeia de sinal. */
enum class EffectSlotType(val label: String, val fullName: String) {
    GATE("GATE", "Noise Gate"),
    CMP("CMP", "Compressor"),
    EQ("EQ", "EQ Global"),
    MOD("MOD", "Modulacao"),
    DLY("DLY", "Delay"),
    REV("REV", "Reverb"),
    CAB("CAB", "Cabinet")
}

/** Arte oficial de cada bloco (extraida de assets/Paks do TONEX Control). */
private fun EffectSlotType.gearRes(): Int? = when (this) {
    EffectSlotType.GATE -> R.drawable.gear_gate
    EffectSlotType.CMP -> R.drawable.gear_comp
    EffectSlotType.EQ -> null
    EffectSlotType.MOD -> R.drawable.gear_mod
    EffectSlotType.DLY -> R.drawable.gear_delay
    EffectSlotType.REV -> R.drawable.gear_reverb
    EffectSlotType.CAB -> R.drawable.gear_cab
}

@Composable
fun EditorScreen(
    activeSlot: Slot,
    activePreset: PresetSlot?,
    bypassMode: Boolean,
    cabSimBypass: Boolean,
    ampKnobs: AmpKnobUiState,
    busyReason: String?,
    effectChain: EffectChainUiState,
    rigModels: RigModels = RigModels(null, null, null),
    /** Nomes manuais de amp/cab do preset ativo (personalizacao local); null = derivado. */
    ampNameOverride: String? = null,
    cabNameOverride: String? = null,
    onAmpKnobChange: (AmpKnob, Float) -> Unit,
    onSelectEffect: (EffectSlotType) -> Unit,
    onToggleEffect: (EffectSlotType) -> Unit,
    modifier: Modifier = Modifier
) {
    val cabLabel = cabNameOverride ?: rigModels.cabLabel(cabSimBypass)
    // Acento do Editor segue a cor do preset ativo (LED do pedal), como no app oficial.
    val accent = activePreset?.color?.toColor() ?: ToneXAccent
    // Em tablets o conteudo fica centrado com largura maxima (nao esticado de ponta a ponta).
    Box(
        modifier = modifier.fillMaxSize().tusbBackground(),
        contentAlignment = Alignment.TopCenter
    ) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (busyReason != null) {
            item { BusyStatusBanner(busyReason = busyReason) }
        }
        item {
            TitleRow(activeSlot = activeSlot, presetName = activePreset?.name, bypassMode = bypassMode)
        }
        item {
            SignalChainCarousel(
                effectChain = effectChain,
                ampEnabled = rigModels.ampEnabled,
                cabLabel = cabLabel,
                accent = accent,
                onSelectEffect = onSelectEffect,
                onToggleEffect = onToggleEffect
            )
        }
        item { Divider() }
        item {
            AmpInfoRow(
                presetName = ampNameOverride ?: activePreset?.name ?: "Preset ${activeSlot.name}",
                ampEnabled = rigModels.ampEnabled,
                cabLabel = cabLabel,
                accent = accent
            )
        }
        item { Divider() }
        item {
            // EQ: BASS MID TREBLE (knobs medios)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AmpKnobControl(AmpKnob.BASS, ampKnobs, KnobSize.MEDIUM, accent, onAmpKnobChange)
                AmpKnobControl(AmpKnob.MID, ampKnobs, KnobSize.MEDIUM, accent, onAmpKnobChange)
                AmpKnobControl(AmpKnob.TREBLE, ampKnobs, KnobSize.MEDIUM, accent, onAmpKnobChange)
            }
        }
        item {
            // GAIN VOLUME (knobs grandes)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AmpKnobControl(AmpKnob.GAIN, ampKnobs, KnobSize.LARGE, accent, onAmpKnobChange)
                AmpKnobControl(AmpKnob.VOLUME, ampKnobs, KnobSize.LARGE, accent, onAmpKnobChange)
            }
        }
    }
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ToneXDivider))
}

@Composable
private fun TitleRow(activeSlot: Slot, presetName: String?, bypassMode: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = activeSlot.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = presetName ?: "Preset ${activeSlot.name}",
            fontSize = 18.sp,
            color = ToneXOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        Text(
            text = if (bypassMode) "BYPASS" else "LIVE",
            style = MonoLabelStyle,
            color = if (bypassMode) MaterialTheme.colorScheme.error else ToneXGreen
        )
    }
}

/**
 * Carrossel da cadeia de sinal: pedais de efeito com o amp TUSB no centro, ligados
 * por uma linha continua, como no design. Toque no pedal abre o detalhe; toque no
 * LED liga/desliga o bloco.
 */
@Composable
private fun SignalChainCarousel(
    effectChain: EffectChainUiState,
    ampEnabled: Boolean?,
    cabLabel: String,
    accent: Color,
    onSelectEffect: (EffectSlotType) -> Unit,
    onToggleEffect: (EffectSlotType) -> Unit
) {
    val movable = EffectSlotType.entries.filter { it != EffectSlotType.CAB }
    val preAmp = movable.filterNot(effectChain::isPost)
    val postAmp = movable.filter(effectChain::isPost) + EffectSlotType.CAB
    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)
        ) {
            items(preAmp) { effect ->
                PedalCard(
                    effect = effect,
                    enabled = effectChain.isEnabled(effect),
                    onClick = { onSelectEffect(effect) },
                    onToggle = { onToggleEffect(effect) }
                )
            }
            item { AmpHeadCard(dimmed = ampEnabled == false, accent = accent) }
            items(postAmp) { effect ->
                PedalCard(
                    effect = effect,
                    enabled = effectChain.isEnabled(effect),
                    labelOverride = if (effect == EffectSlotType.CAB) "CAB · $cabLabel" else null,
                    onClick = { onSelectEffect(effect) },
                    onToggle = { onToggleEffect(effect) }
                )
            }
        }
    }
}

@Composable
private fun PedalCard(
    effect: EffectSlotType,
    enabled: Boolean,
    labelOverride: String? = null,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(78.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF09090A))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        val gearRes = effect.gearRes()
        if (gearRes != null) {
            Image(
                painter = painterResource(gearRes),
                contentDescription = effect.fullName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 14.dp, bottom = 18.dp, start = 4.dp, end = 4.dp)
                    .alpha(if (enabled) 1f else 0.45f),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = effect.fullName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp, bottom = 24.dp)
                    .alpha(if (enabled) 1f else 0.45f),
                tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.45f)
            )
        }
        // LED de estado (toque liga/desliga)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (enabled) ToneXGreen else ToneXSurfaceHigh)
                .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                .clickable(onClick = onToggle)
        )
        Text(
            text = labelOverride ?: effect.label,
            style = MonoLabelStyle.copy(fontSize = 8.sp, letterSpacing = 0.4.sp),
            color = Color(0xFFAAAAAA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 3.dp, horizontal = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Amp central com a arte oficial (Amp.png do TONEX Control) e borda na cor do preset. */
@Composable
private fun AmpHeadCard(dimmed: Boolean, accent: Color = ToneXAccent) {
    Box(
        modifier = Modifier
            .width(170.dp)
            .height(110.dp)
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF09090A))
            .border(2.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.gear_amp),
            contentDescription = "Amp",
            modifier = Modifier.fillMaxSize().padding(6.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = "TUSB",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Color(0xFFC8A04A),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
        )
    }
}

/**
 * Linha de info do rig: o "amp" do ToneX One e' o Tone Model do preset ativo; ao lado,
 * o cab em uso (Tone Model / VIR n / Off). Dados do detalhe 0x0304 + cab_sim_bypass.
 */
@Composable
private fun AmpInfoRow(presetName: String, ampEnabled: Boolean?, cabLabel: String, accent: Color = ToneXAccent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (ampEnabled == false) "AMP OFF" else "AMP",
            style = MonoLabelStyle,
            color = if (ampEnabled == false) ToneXOnSurfaceMuted else accent
        )
        Text(
            text = presetName,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.12f)))
        Text(text = "CAB", style = MonoLabelStyle, color = ToneXOnSurfaceMuted)
        Text(
            text = cabLabel,
            style = MonoLabelStyle,
            color = if (cabLabel == "OFF") ToneXOnSurfaceMuted else ToneXGreen
        )
    }
}

@Composable
private fun AmpKnobControl(
    knob: AmpKnob,
    ampKnobs: AmpKnobUiState,
    size: KnobSize,
    accent: Color,
    onAmpKnobChange: (AmpKnob, Float) -> Unit
) {
    val value = ampKnobs.valueOf(knob).coerceIn(0f, 1f)
    TusbKnob(
        label = knob.label,
        value = value,
        valueText = String.format("%.1f", value * 10f),
        size = size,
        accent = accent,
        onValueChange = { onAmpKnobChange(knob, it) }
    )
}

private fun Rgb.toColor(): Color = Color(r, g, b)

@Composable
private fun BusyStatusBanner(busyReason: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = busyReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
