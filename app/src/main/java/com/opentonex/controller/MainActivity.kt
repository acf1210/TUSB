package com.opentonex.controller

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import java.io.File
import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.midi.MidiController
import com.opentonex.controller.midi.PedalMidiActionHandler
import com.opentonex.controller.ui.PedalViewModel
import com.opentonex.controller.ui.ToneXApp
import com.opentonex.controller.ui.theme.ToneXTheme
import com.opentonex.controller.usb.UsbSerialTransport

class MainActivity : ComponentActivity() {
    // A Activity e o Compose precisam da MESMA instancia do ViewModel para que o MIDI
    // (criado aqui) e a UI (viewModel() no Compose) enxerguem o mesmo estado do pedal.
    private val pedalViewModel: PedalViewModel by viewModels()
    private var midiController: MidiController? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val midi = MidiController(
            context = applicationContext,
            handler = PedalMidiActionHandler(pedalViewModel)
        )
        midiController = midi
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ToneXTheme {
                ToneXApp(
                    windowSizeClass = windowSizeClass,
                    onCreateRealConnection = { createRealConnection() },
                    onCreateFakeConnection = { FakePedalConnection() },
                    onResolveCaptureDirectory = { resolveCaptureDirectory() },
                    midiController = midi,
                    viewModel = pedalViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        midiController?.release()
        midiController = null
        super.onDestroy()
    }

    private suspend fun createRealConnection(): PedalConnection? {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager) ?: return null
        return UsbPedalConnection(transport)
    }

    private fun resolveCaptureDirectory(): File {
        val externalRoot = getExternalFilesDir(null)
        return File(externalRoot ?: filesDir, "event-captures")
    }
}
