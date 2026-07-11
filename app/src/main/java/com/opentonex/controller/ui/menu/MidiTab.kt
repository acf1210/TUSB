package com.opentonex.controller.ui.menu

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentonex.controller.R
import com.opentonex.controller.midi.MidiAction
import com.opentonex.controller.midi.MidiConnectionState
import com.opentonex.controller.midi.MidiController
import com.opentonex.controller.midi.MidiMessage
import com.opentonex.controller.midi.midiMappingLabel

/** Permissoes de runtime necessarias para o scan BLE conforme a versao do Android. */
private fun bleScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun MidiTab(controller: MidiController?) {
    if (controller == null || !controller.isMidiSupported) {
        Text(
            text = stringResource(R.string.midi_not_supported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val devices by controller.devices.collectAsStateWithLifecycle()
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val mapping by controller.mapping.collectAsStateWithLifecycle()
    val learnTarget by controller.learnTarget.collectAsStateWithLifecycle()
    val lastMessage by controller.lastMessage.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            permissionDenied = false
            controller.startBleScan()
        } else {
            permissionDenied = true
        }
    }

    // --- Dispositivos ---
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.midi_devices_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            when (val state = connectionState) {
                is MidiConnectionState.Connected -> Text(
                    text = stringResource(R.string.midi_connected_to, state.deviceName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                is MidiConnectionState.Connecting -> Text(
                    text = stringResource(R.string.midi_connecting_to, state.deviceName),
                    style = MaterialTheme.typography.bodyMedium
                )
                is MidiConnectionState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { permissionLauncher.launch(bleScanPermissions()) },
                    enabled = connectionState !is MidiConnectionState.Scanning
                ) {
                    Text(
                        stringResource(
                            if (connectionState is MidiConnectionState.Scanning) {
                                R.string.midi_scanning
                            } else {
                                R.string.midi_scan
                            }
                        )
                    )
                }
                OutlinedButton(onClick = { controller.refreshUsbDevices() }) {
                    Text(stringResource(R.string.midi_refresh_usb))
                }
            }
            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.midi_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.midi_no_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            devices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (if (device.isBluetooth) "BLE · " else "USB · ") + device.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val isConnectedToThis = (connectionState as? MidiConnectionState.Connected)
                        ?.deviceName == device.name
                    if (isConnectedToThis) {
                        TextButton(onClick = { controller.disconnect() }) {
                            Text(stringResource(R.string.midi_disconnect))
                        }
                    } else {
                        TextButton(onClick = { controller.connect(device) }) {
                            Text(stringResource(R.string.midi_connect))
                        }
                    }
                }
            }
        }
    }

    // --- Mapeamento ---
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.midi_mapping_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.midi_pc_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MidiAction.entries.forEach { action ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = action.label, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mapping.ccFor(action)?.let { midiMappingLabel(it) }
                                ?: stringResource(R.string.midi_cc_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (learnTarget == action) {
                            TextButton(onClick = { controller.cancelLearn() }) {
                                Text(stringResource(R.string.midi_cancel))
                            }
                            Text(
                                text = stringResource(R.string.midi_learning),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = { controller.startLearn(action) }) {
                                Text(stringResource(R.string.midi_learn))
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { controller.resetMapping() }) {
                Text(stringResource(R.string.midi_restore_default))
            }
            val last = lastMessage
            if (last != null) {
                val description = when (last) {
                    is MidiMessage.ProgramChange -> "PC ${last.program}"
                    is MidiMessage.ControlChange -> "CC ${last.controller} = ${last.value}"
                }
                Text(
                    text = stringResource(R.string.midi_last_message, description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
