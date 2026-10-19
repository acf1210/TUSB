package com.opentonex.controller.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.opentonex.controller.R
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.midi.MidiController
import com.opentonex.controller.protocol.GlobalSetting
import com.opentonex.controller.ui.theme.TusbTheme

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
    masterVolume: Float?,
    theme: TusbTheme,
    onMasterVolumeChange: (Float) -> Unit,
    onGlobalSettingChange: (GlobalSetting, Float) -> Unit,
    onThemeChange: (TusbTheme) -> Unit,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit,
    midiController: MidiController? = null,
    /** Cartao de atualizacoes do app, exibido na aba Geral. */
    updateSettings: (@Composable () -> Unit)? = null,
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
                MenuTab.VOLUME -> VolumeSettings(
                    masterVolume = masterVolume,
                    pedal = pedal,
                    onMasterVolumeChange = onMasterVolumeChange,
                    onGlobalSettingChange = onGlobalSettingChange
                )
                MenuTab.MIDI -> MidiTab(controller = midiController)
                MenuTab.TUNER -> TunerSettings(pedal = pedal, onGlobalSettingChange = onGlobalSettingChange)
                MenuTab.GENERAL -> {
                    TempoSettings(pedal = pedal, onGlobalSettingChange = onGlobalSettingChange)
                    GeneralTab(theme = theme, onThemeChange = onThemeChange)
                    updateSettings?.invoke()
                }
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
private fun GeneralTab(theme: TusbTheme, onThemeChange: (TusbTheme) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.menu_theme), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TusbTheme.entries.forEach { option ->
                    FilterChip(
                        selected = theme == option,
                        onClick = { onThemeChange(option) },
                        label = { Text(option.label) }
                    )
                }
            }
            Text(
                text = stringResource(R.string.menu_theme_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

