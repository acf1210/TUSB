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
import com.opentonex.controller.protocol.FullPreset
import com.opentonex.controller.protocol.GlobalSetting
import com.opentonex.controller.protocol.TonexFullPreset
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Volume master (0..10) informado pelo pedal; null ate a primeira resposta. */
    private val _masterVolume = MutableStateFlow<Float?>(null)
    val masterVolume: StateFlow<Float?> = _masterVolume.asStateFlow()
    private var masterVolumeRequests = 0
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
        masterVolumeRequests = 0
        requestMasterVolumeIfMissing()
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

    /**
     * Grava um ajuste global (trim, A4, tempo...) reenviando o estado com o campo alterado.
     * Lanca [IllegalArgumentException] se o estado do pedal nao tiver o layout conhecido.
     */
    suspend fun setGlobal(setting: GlobalSetting, value: Float) = operationMutex.withLock {
        val current = _state.value as? ConnectionState.Connected ?: return
        val updated = current.pedal.withGlobal(setting, value)
        recordLocalAction("set_global", mapOf("setting" to setting.name, "value" to value))
        _state.value = current.copy(pedal = updated)
        connection.writeGlobalState(updated)
    }

    suspend fun setMasterVolume(value: Float) = operationMutex.withLock {
        if (_state.value !is ConnectionState.Connected) return
        recordLocalAction("set_master_volume", mapOf("value" to value))
        _masterVolume.value = value
        connection.writeMasterVolume(value)
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

    /**
     * Le o preset completo (com o Tone Model) sob o mesmo mutex do poll: os ~30 KB do 0x0303
     * nao podem ser disputados pela drenagem passiva de frames.
     */
    suspend fun readFullPreset(bank: Int, index: Int): FullPreset = operationMutex.withLock {
        if (_state.value !is ConnectionState.Connected) throw IllegalStateException("pedal desconectado")
        recordLocalAction("read_full_preset", mapOf("bank" to bank, "index" to index))
        TonexFullPreset.parse(connection.readFullPreset(bank, index))
    }

    /**
     * Grava [body] no preset salvo [targetIndex] e confirma relendo o preset.
     * Retorna true quando o pedal devolveu exatamente o conteudo gravado.
     */
    suspend fun writeFullPreset(body: ByteArray, targetIndex: Int): Boolean = operationMutex.withLock {
        if (_state.value !is ConnectionState.Connected) throw IllegalStateException("pedal desconectado")
        recordLocalAction("write_full_preset", mapOf("index" to targetIndex, "size" to body.size))
        connection.writeFullPreset(body, targetIndex)
        delay(FULL_PRESET_SETTLE_MS) // o pedal grava na flash antes de responder a releitura
        val readBack = connection.readFullPreset(TonexFullPreset.BANK_STORED, targetIndex)
        TonexFullPreset.sameContent(body, readBack)
    }

    suspend fun refreshState() {
        syncState(recordAction = true)
    }

    suspend fun syncStateFromPedal() {
        val conn = connection as? UsbPedalConnection
        if (conn == null) {
            operationMutex.withLock { requestMasterVolumeIfMissing() }
            syncState(recordAction = false)
            return
        }
        operationMutex.withLock {
            val current = _state.value as? ConnectionState.Connected ?: return
            requestMasterVolumeIfMissing()
            // Drena a rajada inteira (knob fisico gera dezenas de 0x0309 entre polls).
            val passiveState = conn.drainPassiveFrames() ?: return
            _state.value = current.copy(pedal = reconcilePedal(passiveState))
        }
    }

    /**
     * Pede o volume master enquanto ele nao chegar. A resposta vem pelo caminho passivo
     * (0x0309), e o pedal descarta parte dos comandos: o pedido feito logo apos o handshake
     * caiu na bancada. Repete nos polls, com limite, e falhar aqui nao derruba a conexao.
     */
    private suspend fun requestMasterVolumeIfMissing() {
        if (_state.value !is ConnectionState.Connected) return
        if (_masterVolume.value != null || masterVolumeRequests >= MASTER_VOLUME_REQUEST_ATTEMPTS) return
        masterVolumeRequests++
        runCatching { connection.requestMasterVolume() }
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
        _masterVolume.value = null
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
            is PedalRuntimeEvent.MasterVolumeChanged -> _masterVolume.value = event.value
            PedalRuntimeEvent.Disconnected -> {
                _masterVolume.value = null
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

    private companion object {
        const val MASTER_VOLUME_REQUEST_ATTEMPTS = 10
        const val FULL_PRESET_SETTLE_MS = 500L
    }
}
