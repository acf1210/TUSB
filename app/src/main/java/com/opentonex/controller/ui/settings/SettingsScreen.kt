package com.opentonex.controller.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    firmwareVersion: String,
    isBusy: Boolean,
    busyReason: String?,
    isCapturing: Boolean,
    captureFilePath: String?,
    lastCaptureFilePath: String?,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Configuracoes", style = MaterialTheme.typography.titleLarge)
        Text(text = "Firmware do pedal: $firmwareVersion")
        if (busyReason != null) {
            Text(
                text = busyReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "ToneX Controller - controle nao-oficial via USB-C para o " +
                "IK Multimedia ToneX One (versao sem Bluetooth).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRefreshState, enabled = !isBusy) {
            Text("Atualizar estado do pedal")
        }
        if (isCapturing) {
            Button(onClick = onStopCapture, enabled = !isBusy) {
                Text("Parar captura JSONL")
            }
        } else {
            Button(onClick = onStartCapture, enabled = !isBusy) {
                Text("Iniciar captura JSONL")
            }
        }
        if (captureFilePath != null) {
            Text(text = "Capturando em:\n$captureFilePath", style = MaterialTheme.typography.bodySmall)
        } else if (lastCaptureFilePath != null) {
            Text(text = "Ultima captura:\n$lastCaptureFilePath", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onDisconnect, enabled = !isBusy) {
            Text("Desconectar")
        }
    }
}
