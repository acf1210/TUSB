package com.opentonex.controller.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentonex.controller.domain.PedalState

@Composable
fun EditorScreen(pedal: PedalState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Parametros globais (somente leitura)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Edicao de parametros sera habilitada quando o protocolo de escrita " +
                "desses campos for decifrado em uma fase futura.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ReadOnlyField(label = "Input trim", value = "%.2f dB".format(pedal.inputTrim))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReadOnlyField(label = "Referencia A4", value = "${pedal.a4Reference} Hz")
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReadOnlyField(label = "Tempo", value = "${pedal.tempo} BPM")
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
