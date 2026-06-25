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
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Configuracoes", style = MaterialTheme.typography.titleLarge)
        Text(text = "Firmware do pedal: $firmwareVersion")
        Text(
            text = "ToneX Controller - controle nao-oficial via USB-C para o " +
                "IK Multimedia ToneX One (versao sem Bluetooth).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onDisconnect) {
            Text("Desconectar")
        }
    }
}
