package com.opentonex.controller

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.ui.ToneXApp
import com.opentonex.controller.ui.theme.ToneXTheme
import com.opentonex.controller.usb.UsbSerialTransport

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ToneXTheme {
                ToneXApp(
                    windowSizeClass = windowSizeClass,
                    onCreateRealConnection = { createRealConnection() },
                    onCreateFakeConnection = { FakePedalConnection() }
                )
            }
        }
    }

    private suspend fun createRealConnection(): PedalConnection? {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager) ?: return null
        return UsbPedalConnection(transport)
    }
}
