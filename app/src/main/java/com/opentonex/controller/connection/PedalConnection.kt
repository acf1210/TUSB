package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import kotlinx.coroutines.flow.Flow

/**
 * Resultado do handshake inicial. O Hello do pedal devolve um unico frame 0x0306 que ja
 * contem TODO o estado (ver docs/protocol-notes.md), entao firmware e estado vem do mesmo
 * round-trip - nao e preciso um requestState() separado ao conectar.
 */
data class Handshake(val firmware: FirmwareInfo, val state: PedalState)

interface PedalConnection {
    val runtimeEvents: Flow<PedalRuntimeEvent>
    suspend fun connect()
    /** Um unico round-trip de conexao: envia o Hello e extrai firmware + estado da resposta 0x0306. */
    suspend fun handshake(): Handshake
    suspend fun requestState(): PedalState
    suspend fun writeState(state: PedalState)
    /** Troca o preset ativo pelo seu [presetId] na biblioteca do pedal (comando real, tipo 0x0300). */
    suspend fun selectPreset(presetId: Int)
    /** Alterna o modo operacional do pedal (AB <-> STOMP). */
    suspend fun switchMode(currentState: PedalState, targetMode: PedalMode)
    suspend fun disconnect()
}
