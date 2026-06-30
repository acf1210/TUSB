package com.opentonex.controller.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

@Composable
fun HomeScreen(
    firmwareVersion: String,
    activeSlot: Slot,
    pedalMode: PedalMode,
    presets: List<PresetSlot>,
    isBusy: Boolean,
    busyReason: String?,
    onSelectSlot: (Slot) -> Unit,
    onSwitchMode: (PedalMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Firmware: $firmwareVersion",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val visibleSlots = if (pedalMode == PedalMode.STOMP) Slot.entries else listOf(Slot.A, Slot.B)
                visibleSlots.forEach { slot ->
                    FilterChip(
                        selected = slot == activeSlot,
                        enabled = !isBusy,
                        onClick = { onSelectSlot(slot) },
                        label = { Text(slot.name) }
                    )
                }
            }
            val targetMode = if (pedalMode == PedalMode.STOMP) PedalMode.AB else PedalMode.STOMP
            OutlinedButton(
                onClick = { onSwitchMode(targetMode) },
                enabled = !isBusy
            ) {
                Text(if (pedalMode == PedalMode.STOMP) "Modo A/B" else "Modo Stomp")
            }
        }
        if (busyReason != null) {
            Text(
                text = busyReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@Composable
private fun PresetRow(preset: PresetSlot, isActive: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ColorSwatch(preset.color)
            Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
            if (isActive) {
                Text(
                    text = "ativo",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Rgb) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(color.r, color.g, color.b))
    )
}
