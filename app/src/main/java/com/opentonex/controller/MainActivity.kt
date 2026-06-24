package com.opentonex.controller

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.repository.PedalRepository
import com.opentonex.controller.usb.UsbSerialTransport
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var status by remember { mutableStateOf("Desconectado") }
            val scope = rememberCoroutineScope()

            Column(modifier = Modifier.padding(16.dp)) {
                Text("ToneX Controller")
                Text(status)
                Button(onClick = {
                    scope.launch {
                        status = "Conectando..."
                        status = connectToRealPedal()
                    }
                }) {
                    Text("Conectar pedal (debug)")
                }
            }
        }
    }

    private suspend fun connectToRealPedal(): String = try {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager)
            ?: return "Pedal nao encontrado via USB"
        val repository = PedalRepository(UsbPedalConnection(transport))
        repository.connect()
        when (val state = repository.state.value) {
            is ConnectionState.Connected ->
                "Conectado: firmware ${state.firmware.version} - slot ${state.pedal.activeSlot}"
            ConnectionState.Disconnected -> "Falha ao conectar"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }
}
