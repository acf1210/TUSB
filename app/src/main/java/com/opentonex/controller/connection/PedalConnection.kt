package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
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
    /** Carrega [presetId] em [slot], opcionalmente tornando esse slot ativo. */
    suspend fun loadPresetToSlot(currentState: PedalState, presetId: Int, slot: Slot, selectSlot: Boolean)
    /** Alterna o modo operacional do pedal (AB <-> STOMP). */
    suspend fun switchMode(currentState: PedalState, targetMode: PedalMode)
    /**
     * Escreve UM parametro do preset ativo (comando 0x0309, sem reenviar o preset inteiro).
     * [paramIndex] e o indice na tabela tonex_params e [value] o valor real (ex.: gain 0..10).
     */
    suspend fun writeParameter(paramIndex: Int, value: Float)
    /**
     * Grava os ajustes globais (trim, A4, tempo...) reenviando [state].rawState, que ja traz os
     * bytes alterados, com o slot e o bypass de [state]. O padrao reaproveita [writeState].
     */
    suspend fun writeGlobalState(state: PedalState) = writeState(state)
    /** Volume master do pedal (0..10), comando proprio fora do StateData. */
    suspend fun writeMasterVolume(value: Float) {}
    /** Pede o volume master; a resposta chega como [PedalRuntimeEvent.MasterVolumeChanged]. */
    suspend fun requestMasterVolume() {}
    /**
     * Le o preset completo (0x0303, com o Tone Model) e devolve o corpo exato do pedal.
     * [bank] 1 = presets salvos 0..19; 0 = preset em edicao.
     */
    suspend fun readFullPreset(bank: Int, index: Int): ByteArray =
        throw UnsupportedOperationException("leitura de preset completo nao suportada")
    /** Grava um corpo 0x0303 (preset completo, com Tone Model) no preset salvo [targetIndex]. */
    suspend fun writeFullPreset(body: ByteArray, targetIndex: Int): Unit =
        throw UnsupportedOperationException("gravacao de preset completo nao suportada")
    suspend fun disconnect()
}
