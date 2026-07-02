package com.opentonex.controller.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import com.opentonex.controller.R
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.AmpKnobUiState

private val PanelShape = RoundedCornerShape(8.dp)

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

@Composable
fun EditorScreen(
    activeSlot: Slot,
    activePreset: PresetSlot?,
    bypassMode: Boolean,
    cabSimBypass: Boolean,
    ampKnobs: AmpKnobUiState,
    busyReason: String?,
    effectChain: Map<EffectSlotType, Boolean>,
    onAmpKnobChange: (AmpKnob, Float) -> Unit,
    onSelectEffect: (EffectSlotType) -> Unit,
    onToggleEffect: (EffectSlotType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (busyReason != null) {
            item {
                BusyStatusBanner(busyReason = busyReason)
            }
        }
        item {
            AmpEditorPanel(
                activeSlot = activeSlot,
                activePreset = activePreset,
                cabSimBypass = cabSimBypass,
                bypassMode = bypassMode,
                ampKnobs = ampKnobs,
                onAmpKnobChange = onAmpKnobChange
            )
        }
        item {
            EffectChainStrip(
                effectChain = effectChain,
                onSelectEffect = onSelectEffect,
                onToggleEffect = onToggleEffect
            )
        }
    }
}

/**
 * Banner de progresso (ex.: "Conectando...", "Trocando preset..."). O card com titulo do
 * dispositivo e versao de firmware foi removido daqui: essa informacao agora vive na barra
 * superior (TopBrandBar em ToneXApp.kt), junto com a marca do app.
 */
@Composable
private fun BusyStatusBanner(busyReason: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = PanelShape
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

@Composable
private fun AmpEditorPanel(
    activeSlot: Slot,
    activePreset: PresetSlot?,
    cabSimBypass: Boolean,
    bypassMode: Boolean,
    ampKnobs: AmpKnobUiState,
    onAmpKnobChange: (AmpKnob, Float) -> Unit
) {
    val accent = activePreset?.color?.toColor() ?: slotColor(activeSlot)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111113),
        shape = PanelShape
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activePreset?.name ?: "Preset ${activeSlot.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (bypassMode) "BYPASS" else "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bypassMode) MaterialTheme.colorScheme.error else Color(0xFF5CFF6A)
                )
            }
            RigGraphic(accent = accent, cabSimBypass = cabSimBypass)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TonexKnob(knob = AmpKnob.BASS, value = ampKnobs.valueOf(AmpKnob.BASS), accent = accent, onValueChange = onAmpKnobChange)
                TonexKnob(knob = AmpKnob.MID, value = ampKnobs.valueOf(AmpKnob.MID), accent = accent, onValueChange = onAmpKnobChange)
                TonexKnob(knob = AmpKnob.TREBLE, value = ampKnobs.valueOf(AmpKnob.TREBLE), accent = accent, onValueChange = onAmpKnobChange)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TonexKnob(knob = AmpKnob.GAIN, value = ampKnobs.valueOf(AmpKnob.GAIN), accent = accent, onValueChange = onAmpKnobChange)
                TonexKnob(knob = AmpKnob.VOLUME, value = ampKnobs.valueOf(AmpKnob.VOLUME), accent = accent, onValueChange = onAmpKnobChange)
            }
        }
    }
}

@Composable
private fun RigGraphic(accent: Color, cabSimBypass: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(92.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GearSkin(imageRes = R.drawable.tonex_amp, label = "TONEX", accent = accent, modifier = Modifier.weight(1.18f))
        GearSkin(
            imageRes = if (cabSimBypass) R.drawable.slvrface else R.drawable.fndrtwin,
            label = if (cabSimBypass) "CAB OFF" else "CAB",
            accent = if (cabSimBypass) Color(0xFF8A8A8A) else Color(0xFFB68C4A),
            modifier = Modifier.weight(0.98f)
        )
        GearSkin(imageRes = R.drawable.tonex_pedal, label = "STOMP", accent = Color(0xFFD9A13D), modifier = Modifier.weight(0.78f))
    }
}

@Composable
private fun GearSkin(imageRes: Int, label: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF09090A))
            .border(1.dp, accent.copy(alpha = 0.52f), RoundedCornerShape(4.dp))
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = label,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.54f))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(accent.copy(alpha = 0.90f))
        )
    }
}

@Composable
private fun TonexKnob(knob: AmpKnob, value: Float, accent: Color, onValueChange: (AmpKnob, Float) -> Unit) {
    val normalizedValue = value.coerceIn(0f, 1f)
    val latestValue = rememberUpdatedState(normalizedValue)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .pointerInput(knob) {
                    var startValue = 0f
                    var accumulatedDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            startValue = latestValue.value
                            accumulatedDrag = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        accumulatedDrag -= dragAmount / 220f
                        onValueChange(knob, startValue + accumulatedDrag)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val startAngle = 135f
            val sweep = 270f * normalizedValue
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFF2C2C2E),
                    startAngle = startAngle,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                val angle = Math.toRadians((startAngle + sweep).toDouble())
                val radius = size.minDimension * 0.28f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    end = androidx.compose.ui.geometry.Offset(
                        centerX + cos(angle).toFloat() * radius,
                        centerY + sin(angle).toFloat() * radius
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1B1D))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            )
        }
        Text(text = knob.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "${(normalizedValue * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun EffectChainStrip(
    effectChain: Map<EffectSlotType, Boolean>,
    onSelectEffect: (EffectSlotType) -> Unit,
    onToggleEffect: (EffectSlotType) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = PanelShape) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.editor_effect_chain), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(EffectSlotType.entries.toList()) { effect ->
                    EffectPedalButton(
                        effect = effect,
                        enabled = effectChain[effect] ?: true,
                        onClick = { onSelectEffect(effect) },
                        onToggleEnabled = { onToggleEffect(effect) }
                    )
                }
            }
            Text(
                text = stringResource(R.string.editor_effect_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun EffectSlotType.iconRes(): Int? = when (this) {
    EffectSlotType.GATE -> R.drawable.gear_gate
    EffectSlotType.CMP -> R.drawable.gear_comp
    EffectSlotType.MOD -> R.drawable.gear_mod
    EffectSlotType.DLY -> R.drawable.gear_delay
    EffectSlotType.REV -> R.drawable.gear_reverb
    EffectSlotType.CAB -> R.drawable.gear_cab
    EffectSlotType.EQ -> null
}

@Composable
private fun EffectPedalButton(
    effect: EffectSlotType,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = Modifier.width(96.dp).height(104.dp).clickable(onClick = onClick),
        color = Color(0xFF09090A),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val iconRes = effect.iconRes()
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = effect.fullName,
                    modifier = Modifier.fillMaxSize().padding(top = 18.dp, bottom = 20.dp).alpha(contentAlpha),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = effect.fullName,
                    modifier = Modifier.fillMaxSize().padding(top = 22.dp, bottom = 24.dp).alpha(contentAlpha),
                    tint = Color.White.copy(alpha = contentAlpha)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF5CFF6A) else Color(0xFF4A4A4C))
                    .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onToggleEnabled)
            )
            Text(
                text = effect.label,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
    }
}

private fun slotColor(slot: Slot): Color = when (slot) {
    Slot.A -> Color(0xFFE74C3C)
    Slot.B -> Color(0xFF2ECC71)
    Slot.C -> Color(0xFF3498DB)
}

private fun Rgb.toColor(): Color = Color(r, g, b)
