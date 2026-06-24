package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState

interface PedalConnection {
    suspend fun connect()
    suspend fun sendHello(): FirmwareInfo
    suspend fun requestState(): PedalState
    suspend fun writeState(state: PedalState)
    suspend fun disconnect()
}
