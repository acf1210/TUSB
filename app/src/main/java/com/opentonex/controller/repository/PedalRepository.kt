package com.opentonex.controller.repository

import com.opentonex.controller.capture.EventCaptureRecorder
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.PedalRuntimeEvent
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.domain.TonexParam
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Notificacoes 0x0309 do pedal (knob fisico girado), ja decodificadas, para a UI reagir. */
    private val _parameterChanges =
        MutableSharedFlow<PedalRuntimeEvent.ParameterChanged>(extraBufferCapacity = 64)
    val parameterChanges: SharedFlow<PedalRuntimeEvent.ParameterChanged> =
        _parameterChanges.asSharedFlow()
    private var eventRecorder: EventCaptureRecorder? = null
    private var runtimeEventsJob: Job? = null
    private var lastConfirmedActiveSlot: Slot? = null
    private var stablePresetIds: List<Int>? = null
    private val operationMutex = Mutex()

    fun attachCaptureRecorder(recorder: EventCaptureRecorder?) {
        eventRecorder = recorder
    }

    suspend fun connect() = operationMutex.withLock {
        recordLocalAction("connect")
        startRuntimeEventsCollection()
        connection.connect()
        // Um unico round-trip: o Hello ja devolve o estado completo (0x0306), entao nao ha
        // requestState() redundante aqui - menos uma chamada exposta ao timeout na conexao.
        val handshake = connection.handshake()
        stablePresetIds = handshake.state.presetIds.takeIf { it.isNotEmpty() }
        _state.value = ConnectionState.Connected(handshake.firmware, handshake.state)
    }

    suspend fun selectSlot(slot: Slot) = operationMutex.withLock {
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

    suspend fun loadPresetToActiveSlot(presetId: Int) = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        // No modo Stomp o ToneX One trabalha sempre com o slot C (firmware de referencia
        // Builty: stomp_mode=1 <=> slot C). Carregar no slot A/B enquanto em Stomp fazia o
        // comando forcar stomp_mode=0, e o pedal saia do Stomp sem vincular o preset.
        val slot = if (current.pedal.pedalMode == PedalMode.STOMP) Slot.C else current.pedal.activeSlot
        recordLocalAction("load_preset_to_slot", mapOf("presetId" to presetId, "slot" to slot.name))
        val updatedPedal = current.pedal.withPresetInSlot(presetId, slot, selectSlot = true)
        lastConfirmedActiveSlot = slot
        stablePresetIds = updatedPedal.presetIds.takeIf { it.isNotEmpty() }
        _state.value = current.copy(pedal = updatedPedal)
        connection.loadPresetToSlot(current.pedal, presetId, slot, selectSlot = true)
    }

    suspend fun toggleBypass() = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        val newBypass = !current.pedal.bypassMode
        recordLocalAction("toggle_bypass", mapOf("bypass" to newBypass))
        // Otimista: atualiza UI antes da resposta do hardware.
        _state.value = current.copy(pedal = current.pedal.withBypassMode(newBypass))
        val conn = connection
        if (conn is com.opentonex.controller.connection.UsbPedalConnection) {
            conn.writeBypass(current.pedal, newBypass)
        }
    }

    suspend fun toggleCabSimBypass() = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        val newBypass = !current.pedal.cabSimBypass
        recordLocalAction("toggle_cab_sim_bypass", mapOf("bypass" to newBypass))
        _state.value = current.copy(pedal = current.pedal.withCabSimBypass(newBypass))
        val conn = connection
        if (conn is com.opentonex.controller.connection.UsbPedalConnection) {
            conn.writeCabSimBypass(current.pedal, newBypass)
        }
    }

    suspend fun switchMode(targetMode: PedalMode) = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        recordLocalAction("switch_mode", mapOf("target" to targetMode.name))
        connection.switchMode(current.pedal, targetMode)
        // Stomp <=> slot C: alinha o slot local ao que o comando gravou no pedal, senao o
        // reconcilePedal (override de slot) regravava o slot antigo e desfazia o modo.
        val newSlot = when {
            targetMode == PedalMode.STOMP -> Slot.C
            current.pedal.activeSlot == Slot.C -> Slot.A
            else -> current.pedal.activeSlot
        }
        lastConfirmedActiveSlot = newSlot
        _state.value = current.copy(
            pedal = current.pedal.withPedalMode(targetMode).withActiveSlot(newSlot)
        )
    }

    /** Escreve [value] (valor REAL, ex.: gain 0..10) no parametro [param] do preset ativo. */
    suspend fun writeParameter(param: TonexParam, value: Float) =
        writeParameterIndex(param.index, value)

    /**
     * Escreve por indice bruto da tabela tonex_params. Usado pelos controles de efeito
     * com indice dinamico por modelo (reverb/modulacao/delay - ver TonexEffectParams).
     */
    suspend fun writeParameterIndex(index: Int, value: Float) = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        recordLocalAction("write_parameter", mapOf("index" to index, "value" to value))
        connection.writeParameter(index, value)
        // Write-through local: o pedal nao reenvia o 0x0304 apos a escrita; sem isto o
        // re-sync do poll restaurava o valor antigo (toggles de efeito nao desativavam).
        _state.value = current.copy(pedal = current.pedal.withParameterValue(index, value))
    }

    suspend fun refreshState() {
        syncState(recordAction = true)
    }

    suspend fun syncStateFromPedal() {
        val conn = connection as? UsbPedalConnection
        if (conn == null) {
            syncState(recordAction = false)
            return
        }
        operationMutex.withLock {
            val current = _state.value as? ConnectionState.Connected ?: return
            // Drena a rajada inteira (knob fisico gera dezenas de 0x0309 entre polls).
            val passiveState = conn.drainPassiveFrames() ?: return
            _state.value = current.copy(pedal = reconcilePedal(passiveState))
        }
    }

    private suspend fun syncState(recordAction: Boolean) = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        if (recordAction) recordLocalAction("refresh_state")
        _state.value = current.copy(pedal = reconcilePedal(connection.requestState()))
    }

    suspend fun disconnect() = operationMutex.withLock {
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
                _state.value = current.copy(
                    pedal = current.pedal.withActivePresetName(event.name)
                        .withActivePresetParameters(event.parameters)
                )
            }
            is PedalRuntimeEvent.StateReceived -> {
                val current = _state.value as? ConnectionState.Connected ?: return
                _state.value = current.copy(pedal = reconcilePedal(event.state))
            }
            is PedalRuntimeEvent.ParameterChanged -> {
                // Espelha o knob fisico no estado local: sem isto o proximo re-sync
                // publicava os parametros antigos e o knob virtual voltava sozinho.
                val current = _state.value as? ConnectionState.Connected
                if (current != null) {
                    _state.value = current.copy(
                        pedal = current.pedal.withParameterValue(event.paramIndex, event.value)
                    )
                }
                _parameterChanges.tryEmit(event)
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
