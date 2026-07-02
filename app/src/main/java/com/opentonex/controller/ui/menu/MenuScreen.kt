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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.opentonex.controller.R
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.midi.MidiController

private enum class MenuTab(@StringRes val labelRes: Int) {
    DEVICE(R.string.menu_device),
    VOLUME(R.string.menu_volume),
    MIDI(R.string.menu_midi),
    TUNER(R.string.menu_tuner),
    GENERAL(R.string.menu_general)
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
    midiController: MidiController? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = MenuTab.entries

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(stringResource(tab.labelRes)) })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                MenuTab.MIDI -> MidiTab(controller = midiController)
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
    Text(text = stringResource(R.string.menu_firmware, firmwareVersion))
    Text(text = stringResource(R.string.menu_active_slot, pedal.activeSlot.name), style = MaterialTheme.typography.bodyMedium)
    if (busyReason != null) {
        Text(text = busyReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        text = stringResource(R.string.menu_about),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = onRefreshState, enabled = !isBusy) { Text(stringResource(R.string.menu_refresh)) }
    if (isCapturing) {
        Button(onClick = onStopCapture, enabled = !isBusy) { Text(stringResource(R.string.menu_stop_capture)) }
    } else {
        Button(onClick = onStartCapture, enabled = !isBusy) { Text(stringResource(R.string.menu_start_capture)) }
    }
    if (captureFilePath != null) {
        Text(text = stringResource(R.string.menu_capturing_in, captureFilePath), style = MaterialTheme.typography.bodySmall)
    } else if (lastCaptureFilePath != null) {
        Text(text = stringResource(R.string.menu_last_capture, lastCaptureFilePath), style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = onDisconnect, enabled = !isBusy) { Text(stringResource(R.string.menu_disconnect)) }
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
                text = stringResource(R.string.menu_master_volume_note),
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
                text = stringResource(R.string.menu_input_trim_real),
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
            Text(text = stringResource(R.string.menu_a4_reference), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { onA4ReferenceChange(a4ReferenceOverride - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.menu_decrease))
                }
                Text(text = "$a4ReferenceOverride Hz", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onA4ReferenceChange(a4ReferenceOverride + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_increase))
                }
            }
            Text(
                text = stringResource(R.string.menu_a4_note, pedalA4Reference),
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
        text = stringResource(R.string.menu_visual_only),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
