package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

class FakePedalConnection : PedalConnection {
    private var state = PedalState(
        activeSlot = Slot.A,
        inputTrim = 0.0f,
        a4Reference = 440,
        tempo = 120,
        slots = listOf(
            PresetSlot(0, "Preset A", Rgb(255, 0, 0)),
            PresetSlot(1, "Preset B", Rgb(0, 255, 0)),
            PresetSlot(2, "Preset C", Rgb(0, 0, 255))
        )
    )

    override suspend fun connect() { /* no-op no simulador */ }
    override suspend fun sendHello(): FirmwareInfo = FirmwareInfo("SIM-1.0.0")
    override suspend fun requestState(): PedalState = state
    override suspend fun writeState(state: PedalState) { this.state = state }
    override suspend fun disconnect() { /* no-op */ }
}
