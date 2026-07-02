package com.opentonex.controller.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentonex.controller.domain.PedalState

private enum class MenuTab(val label: String) {
    DEVICE("Device"),
    VOLUME("Volume"),
    MIDI("MIDI"),
    TUNER("Tuner"),
    GENERAL("General")
}

@Composable
fun MenuScreen(
    firmwareVersion: String,
    pedal: PedalState,
    isBusy: Boolean,
    busyReason: String?,
    isCapturing: Boolean,
    captureFilePath: String?,
    lastCaptureFilePath: String?,
    masterVolume: Float,
    a4ReferenceOverride: Int,
    onMasterVolumeChange: (Float) -> Unit,
    onA4ReferenceChange: (Int) -> Unit,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = MenuTab.entries

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(tab.label) })
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (tabs[selectedTab]) {
                MenuTab.DEVICE -> DeviceTab(
                    firmwareVersion = firmwareVersion,
                    pedal = pedal,
                    isBusy = isBusy,
                    busyReason = busyReason,
                    isCapturing = isCapturing,
                    captureFilePath = captureFilePath,
                    lastCaptureFilePath = lastCaptureFilePath,
                    onRefreshState = onRefreshState,
                    onStartCapture = onStartCapture,
                    onStopCapture = onStopCapture,
                    onDisconnect = onDisconnect
                )
                MenuTab.VOLUME -> VolumeTab(
                    masterVolume = masterVolume,
                    inputTrim = pedal.inputTrim,
                    onMasterVolumeChange = onMasterVolumeChange
                )
                MenuTab.MIDI -> PlaceholderFieldsTab(
                    fields = listOf(
                        "MIDI Channel" to "1",
                        "MIDI Thru" to "Off / Thru / Merge",
                        "Clock Mode" to "Master",
                        "Repeated PC" to "Bypass"
                    )
                )
                MenuTab.TUNER -> TunerTab(
                    a4ReferenceOverride = a4ReferenceOverride,
                    pedalA4Reference = pedal.a4Reference,
                    onA4ReferenceChange = onA4ReferenceChange
                )
                MenuTab.GENERAL -> PlaceholderFieldsTab(fields = listOf("Tempo" to "${pedal.tempo} BPM"))
            }
        }
    }
}

@Composable
private fun DeviceTab(
    firmwareVersion: String,
    pedal: PedalState,
    isBusy: Boolean,
    busyReason: String?,
    isCapturing: Boolean,
    captureFilePath: String?,
    lastCaptureFilePath: String?,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit
) {
    Text(text = "Firmware do pedal: $firmwareVersion")
    Text(text = "Slot ativo: ${pedal.activeSlot.name}", style = MaterialTheme.typography.bodyMedium)
    if (busyReason != null) {
        Text(text = busyReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        text = "TUSB - controle nao-oficial via USB-C para o IK Multimedia ToneX One " +
            "(versao sem Bluetooth).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = onRefreshState, enabled = !isBusy) { Text("Atualizar estado do pedal") }
    if (isCapturing) {
        Button(onClick = onStopCapture, enabled = !isBusy) { Text("Parar captura JSONL") }
    } else {
        Button(onClick = onStartCapture, enabled = !isBusy) { Text("Iniciar captura JSONL") }
    }
    if (captureFilePath != null) {
        Text(text = "Capturando em:\n$captureFilePath", style = MaterialTheme.typography.bodySmall)
    } else if (lastCaptureFilePath != null) {
        Text(text = "Ultima captura:\n$lastCaptureFilePath", style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = onDisconnect, enabled = !isBusy) { Text("Desconectar") }
}

@Composable
private fun VolumeTab(masterVolume: Float, inputTrim: Float, onMasterVolumeChange: (Float) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Master Volume", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(text = "${(masterVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = masterVolume, onValueChange = onMasterVolumeChange)
            Text(
                text = "Ajuste local do app. Ainda nao envia comando de volume ao pedal.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Input Trim", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text = "%.2f dB".format(inputTrim), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Valor real lido do pedal.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TunerTab(a4ReferenceOverride: Int, pedalA4Reference: Int, onA4ReferenceChange: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Referencia A4", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { onA4ReferenceChange(a4ReferenceOverride - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Diminuir")
                }
                Text(text = "$a4ReferenceOverride Hz", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onA4ReferenceChange(a4ReferenceOverride + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Aumentar")
                }
            }
            Text(
                text = "Ajuste local (430-450 Hz). Pedal reporta atualmente $pedalA4Reference Hz; escrita real ainda nao implementada.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceholderFieldsTab(fields: List<Pair<String, String>>) {
    fields.forEach { (label, value) ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(
        text = "Configuracao visual apenas: ainda nao implementada neste controlador.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
