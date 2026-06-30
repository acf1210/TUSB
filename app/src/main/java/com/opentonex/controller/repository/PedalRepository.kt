package com.opentonex.controller.repository

import com.opentonex.controller.capture.EventCaptureRecorder
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.PedalRuntimeEvent
import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connected(val firmware: FirmwareInfo, val pedal: PedalState) : ConnectionState
}

class PedalRepository(
    private val connection: PedalConnection,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private var eventRecorder: EventCaptureRecorder? = null
    private var runtimeEventsJob: Job? = null
    private var lastConfirmedActiveSlot: Slot? = null
    private var stablePresetIds: List<Int>? = null

    fun attachCaptureRecorder(recorder: EventCaptureRecorder?) {
        eventRecorder = recorder
    }

    suspend fun connect() {
        recordLocalAction("connect")
        startRuntimeEventsCollection()
        connection.connect()
        // Um unico round-trip: o Hello ja devolve o estado completo (0x0306), entao nao ha
        // requestState() redundante aqui - menos uma chamada exposta ao timeout na conexao.
        val handshake = connection.handshake()
        stablePresetIds = handshake.state.presetIds.takeIf { it.isNotEmpty() }
        _state.value = ConnectionState.Connected(handshake.firmware, handshake.state)
    }

    suspend fun selectSlot(slot: Slot) {
        val current = _state.value as? ConnectionState.Connected ?: return
        recordLocalAction("select_slot", mapOf("slot" to slot.name))
        // Update otimistico imediato: garante feedback visual mesmo que o presetId nao
        // tenha sido parseado (ex: offset errado no StateResponse).
        lastConfirmedActiveSlot = slot
        _state.value = current.copy(pedal = reconcilePedal(current.pedal.withActiveSlot(slot)))
        val enrichedPedal = current.pedal.withTrustedPresetIds()
        val presetId = TonexMessages.presetIdForSlot(enrichedPedal, slot)
        android.util.Log.d("ToneXRepo", "selectSlot=$slot presetId=$presetId stableIds=$stablePresetIds rawIds=${current.pedal.presetIds}")
        connection.writeState(current.pedal.withActiveSlot(slot))
        recordLocalAction("select_preset_attempt", mapOf("slot" to slot.name, "presetId" to (presetId ?: "null")))
    }

    suspend fun switchMode(targetMode: PedalMode) {
        val current = _state.value as? ConnectionState.Connected ?: return
        recordLocalAction("switch_mode", mapOf("target" to targetMode.name))
        connection.switchMode(current.pedal, targetMode)
        val newState = connection.requestState()
        _state.value = current.copy(pedal = reconcilePedal(newState).copy(pedalMode = TonexMessages.detectMode(newState)))
    }

    suspend fun refreshState() {
        val current = _state.value as? ConnectionState.Connected ?: return
        recordLocalAction("refresh_state")
        _state.value = current.copy(pedal = reconcilePedal(connection.requestState()))
    }

    suspend fun disconnect() {
        recordLocalAction("disconnect")
        runtimeEventsJob?.cancel()
        runtimeEventsJob = null
        connection.disconnect()
        lastConfirmedActiveSlot = null
        stablePresetIds = null
        _state.value = ConnectionState.Disconnected
    }

    /**
     * Inicia a coleta de eventos de runtime e SO retorna quando o coletor ja esta inscrito.
     * Sem esse await, os eventos emitidos durante o handshake (hello/estado) eram perdidos:
     * o SharedFlow sem replay descarta emissoes feitas antes de existir um assinante - por
     * isso "hello" nunca aparecia nas capturas. `onSubscription` garante a inscricao antes
     * do handshake emitir.
     */
    private suspend fun startRuntimeEventsCollection() {
        runtimeEventsJob?.cancel()
        val subscribed = CompletableDeferred<Unit>()
        val events = connection.runtimeEvents
        runtimeEventsJob = scope.launch {
            val ready: Flow<PedalRuntimeEvent> = if (events is SharedFlow<*>) {
                @Suppress("UNCHECKED_CAST")
                (events as SharedFlow<PedalRuntimeEvent>).onSubscription { subscribed.complete(Unit) }
            } else {
                subscribed.complete(Unit)
                events
            }
            ready.collect { event ->
                eventRecorder?.record(event)
                applyRuntimeEvent(event)
            }
        }
        subscribed.await()
    }

    private fun applyRuntimeEvent(event: PedalRuntimeEvent) {
        when (event) {
            is PedalRuntimeEvent.PresetDetailReceived -> {
                val current = _state.value as? ConnectionState.Connected ?: return
                _state.value = current.copy(pedal = current.pedal.withActivePresetName(event.name))
            }
            is PedalRuntimeEvent.StateReceived -> {
                val current = _state.value as? ConnectionState.Connected ?: return
                _state.value = current.copy(pedal = reconcilePedal(event.state))
            }
            PedalRuntimeEvent.Disconnected -> {
                lastConfirmedActiveSlot = null
                stablePresetIds = null
                _state.value = ConnectionState.Disconnected
            }
            else -> Unit
        }
    }

    private fun recordLocalAction(action: String, details: Map<String, Any?> = emptyMap()) {
        eventRecorder?.record(PedalRuntimeEvent.LocalAction(action = action, details = details))
    }

    private fun reconcilePedal(pedal: PedalState): PedalState {
        val trustedPresetPedal = pedal.withTrustedPresetIds()
        val localOverride = lastConfirmedActiveSlot ?: return trustedPresetPedal
        return trustedPresetPedal.withActiveSlot(localOverride)
    }

    private fun PedalState.withTrustedPresetIds(): PedalState {
        val knownIds = stablePresetIds
        if (knownIds.isNullOrEmpty()) {
            stablePresetIds = presetIds.takeIf { it.isNotEmpty() }
            return this
        }
        return withPresetIds(knownIds)
    }
}
