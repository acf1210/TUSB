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
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TonexMessages
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
                Button(onClick = {
                    scope.launch {
                        status = "Capturando Hello..."
                        status = captureHelloRaw()
                    }
                }) {
                    Text("Capturar Hello bruto (debug)")
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

    /**
     * Temporario p/ calibrar parseFirmware (Fase 2, Tarefa 8b): mostra o hex bruto do
     * payload de Hello na tela, sem nenhum parse - o celular nao pode ser host USB do
     * pedal e periferico ADB do PC ao mesmo tempo, entao isso e o jeito de capturar a
     * evidencia sem logcat (mesma limitacao documentada no commit de calibracao do
     * StateResponse).
     */
    private suspend fun captureHelloRaw(): String = try {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager)
            ?: return "Pedal nao encontrado via USB"
        transport.open()
        transport.write(HdlcCodec.encode(TonexMessages.helloPayload()))
        val frame = transport.readFrame(2000L)
        transport.close()
        val payload = when (val decoded = HdlcCodec.decode(frame)) {
            is HdlcFrame.Valid -> decoded.payload
            HdlcFrame.CrcError -> return "CRC invalido | frame bruto: ${frame.toHexDebug()}"
            HdlcFrame.Incomplete -> return "Frame incompleto | bytes brutos: ${frame.toHexDebug()}"
        }
        "Hello (${payload.size}B): ${payload.toHexDebug()}"
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }

    private fun ByteArray.toHexDebug(): String = joinToString(" ") { "%02X".format(it) }
}
