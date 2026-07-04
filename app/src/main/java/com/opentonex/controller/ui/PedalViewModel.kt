package com.opentonex.controller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.opentonex.controller.capture.EventCaptureRecorder
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.domain.TonexEffectParams
import com.opentonex.controller.domain.TonexParam
import com.opentonex.controller.domain.TonexParamBinding
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.repository.PedalRepository
import com.opentonex.controller.ui.editor.EffectSlotType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureUiState(
    val isCapturing: Boolean = false,
    val currentFilePath: String? = null,
    val lastFilePath: String? = null
)

data class UiBusyState(
    val isBusy: Boolean = false,
    val busyReason: String? = null
)

/**
 * Estado local (nao escrito no pedal ainda) de ligado/desligado de cada bloco da cadeia de
 * efeitos. Existe pra sobreviver a navegacao entre a esteira e a tela de detalhe do efeito.
 */
data class EffectChainUiState(
    val enabled: Map<EffectSlotType, Boolean> = EffectSlotType.entries.associateWith { true }
) {
    fun isEnabled(effect: EffectSlotType): Boolean = enabled[effect] ?: true

    fun withToggled(effect: EffectSlotType): EffectChainUiState =
        copy(enabled = enabled + (effect to !isEnabled(effect)))

    /**
     * Sincroniza os toggles com o estado REAL do pedal: enables lidos do bloco de
     * parametros do preset ativo (0x0304) e CAB do cab_sim_bypass do StateData.
     * EQ nao tem enable no protocolo (sempre ativo) e permanece como esta.
     */
    fun withPedalParameters(pedal: PedalState): EffectChainUiState {
        if (pedal.presetParameters.isEmpty()) {
            return copy(enabled = enabled + (EffectSlotType.CAB to !pedal.cabSimBypass))
        }
        val updated = enabled.toMutableMap()
        EffectSlotType.entries.forEach { effect ->
            effect.enableParam()?.let { param ->
                pedal.parameterValue(param)?.let { updated[effect] = it >= 0.5f }
            }
        }
        updated[EffectSlotType.CAB] = !pedal.cabSimBypass
        return copy(enabled = updated)
    }
}

/** Parametro de enable (0/1) de cada bloco; null para CAB (bypass no StateData) e EQ. */
fun EffectSlotType.enableParam(): TonexParam? = when (this) {
    EffectSlotType.GATE -> TonexParam.NOISE_GATE_ENABLE
    EffectSlotType.CMP -> TonexParam.COMP_ENABLE
    EffectSlotType.MOD -> TonexParam.MODULATION_ENABLE
    EffectSlotType.DLY -> TonexParam.DELAY_ENABLE
    EffectSlotType.REV -> TonexParam.REVERB_ENABLE
    EffectSlotType.CAB, EffectSlotType.EQ -> null
}

/** Controles da tela de detalhe de efeito que escrevem no pedal. */
enum class EffectControl { KNOB_A, KNOB_B, KNOB_C, POST, DELAY_SYNC, DELAY_PINGPONG }

/** Valores iniciais da tela de detalhe, derivados do bloco de parametros do preset ativo. */
data class EffectDetailUiState(
    val knobA: Float = 0.35f,
    val knobB: Float = 0.20f,
    val knobC: Float = 0.50f,
    val post: Boolean = true,
    val delaySync: Boolean = false,
    val delayPingPong: Boolean = false
)

/**
 * Ajustes do Menu que ainda nao tem escrita real no pedal (protocolo desses campos nao foi
 * mapeado). Ficam locais no app ate o sub-projeto de mapeamento de parametros confirmar os
 * offsets reais no StateData.
 */
data class MenuUiState(
    val masterVolume: Float = 0.75f,
    val a4ReferenceOverride: Int = 440
)

enum class AmpKnob(val label: String) {
    BASS("BASS"),
    MID("MID"),
    TREBLE("TREBLE"),
    GAIN("GAIN"),
    VOLUME("VOLUME");

    fun parameterIds(): List<String> = when (this) {
        BASS -> listOf("ParameterXEqBass", "EqBass", "bass")
        MID -> listOf("ParameterXEqMid", "EqMid", "mid")
        TREBLE -> listOf("ParameterXEqTreble", "EqTreble", "treble")
        GAIN -> listOf("ParameterXModelGain", "ModelGain", "gain")
        VOLUME -> listOf("ParameterXCompMakeUp", "ParameterXModelVolume", "ModelVolume", "volume")
    }

    /** Parametro real do ToneX One (indice tonex_params + range) que este knob controla. */
    fun toParam(): TonexParam = when (this) {
        BASS -> TonexParam.EQ_BASS
        MID -> TonexParam.EQ_MID
        TREBLE -> TonexParam.EQ_TREBLE
        GAIN -> TonexParam.MODEL_GAIN
        // ponytail: o volume movido no hardware desta bancada chegou como param 8.
        // Voltar para MODEL_VOLUME se uma captura real mostrar param 21 respondendo.
        VOLUME -> TonexParam.COMP_MAKE_UP
    }

    companion object {
        fun fromParamIndex(index: Int): AmpKnob? =
            entries.firstOrNull { it.toParam().index == index }
    }
}

data class AmpKnobUiState(
    val bass: Float = 0.58f,
    val mid: Float = 0.50f,
    val treble: Float = 0.62f,
    val gain: Float = 0.46f,
    val volume: Float = 0.66f
) {
    fun valueOf(knob: AmpKnob): Float = when (knob) {
        AmpKnob.BASS -> bass
        AmpKnob.MID -> mid
        AmpKnob.TREBLE -> treble
        AmpKnob.GAIN -> gain
        AmpKnob.VOLUME -> volume
    }

    fun withValue(knob: AmpKnob, value: Float): AmpKnobUiState {
        val clamped = value.coerceIn(0f, 1f)
        return when (knob) {
            AmpKnob.BASS -> copy(bass = clamped)
            AmpKnob.MID -> copy(mid = clamped)
            AmpKnob.TREBLE -> copy(treble = clamped)
            AmpKnob.GAIN -> copy(gain = clamped)
            AmpKnob.VOLUME -> copy(volume = clamped)
        }
    }

    fun withPedalParameters(pedal: PedalState, skip: Set<AmpKnob> = emptySet()): AmpKnobUiState {
        val activePreset = pedal.slots.getOrNull(pedal.activeSlot.ordinal) ?: return this
        return AmpKnob.entries.fold(this) { state, knob ->
            if (knob in skip) return@fold state
            val parameter = knob.parameterIds()
                .asSequence()
                .mapNotNull(activePreset.parameters::get)
                .firstOrNull()
                ?: return@fold state
            val range = parameter.max - parameter.min
            if (range <= 0f) state else state.withValue(knob, (parameter.value - parameter.min) / range)
        }
    }
}

class PedalViewModel : ViewModel() {
    private var repository: PedalRepository? = null
    private var captureRecorder: EventCaptureRecorder? = null
    private var statePollingJob: Job? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    private val _busy = MutableStateFlow(UiBusyState())
    val busy: StateFlow<UiBusyState> = _busy.asStateFlow()

    private val _ampKnobs = MutableStateFlow(AmpKnobUiState())
    val ampKnobs: StateFlow<AmpKnobUiState> = _ampKnobs.asStateFlow()

    private val _effectChain = MutableStateFlow(EffectChainUiState())
    val effectChain: StateFlow<EffectChainUiState> = _effectChain.asStateFlow()

    fun toggleEffectEnabled(effect: EffectSlotType) {
        if (effect == EffectSlotType.CAB) {
            // CAB nao tem enable na tabela de parametros: usa o cab_sim_bypass do StateData
            // (ja validado em bancada). O estado do toggle e re-sincronizado do pedal.
            _effectChain.value = _effectChain.value.withToggled(effect)
            toggleCabSimBypass()
            return
        }
        val newState = _effectChain.value.withToggled(effect)
        _effectChain.value = newState
        val param = effect.enableParam() ?: return // EQ: sem enable no protocolo, so local
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching {
                repo.writeParameter(param, if (newState.isEnabled(effect)) 1f else 0f)
            }
        }
    }

    /** Valores iniciais da tela de detalhe do [effect], lidos do preset ativo no pedal. */
    fun effectDetail(effect: EffectSlotType): EffectDetailUiState {
        val pedal = (state.value as? ConnectionState.Connected)?.pedal
            ?: return EffectDetailUiState()
        val params = pedal.presetParameters
        if (params.isEmpty()) return EffectDetailUiState()
        val (a, b, c) = effectKnobBindings(effect, pedal)
        fun valueOf(binding: TonexParamBinding?, fallback: Float): Float =
            binding?.let { bind -> params.getOrNull(bind.index)?.let(bind::normalize) } ?: fallback
        val postBinding = effect.postBinding()
        val delayModel = pedal.delayModel()
        return EffectDetailUiState(
            knobA = valueOf(a, 0.35f),
            knobB = valueOf(b, 0.20f),
            knobC = valueOf(c, 0.50f),
            post = postBinding?.let { bind -> (params.getOrNull(bind.index) ?: 1f) >= 0.5f } ?: true,
            delaySync = (params.getOrNull(TonexEffectParams.delaySync(delayModel).index) ?: 0f) >= 0.5f,
            delayPingPong = (params.getOrNull(TonexEffectParams.delayMode(delayModel).index) ?: 0f) >= 0.5f
        )
    }

    /**
     * Aplica um controle da tela de detalhe do efeito no PEDAL: knobs (valor normalizado
     * 0..1, com debounce) e chaves POST/SYNC/PING-PONG (0/1, escrita imediata).
     */
    fun updateEffectControl(effect: EffectSlotType, control: EffectControl, value: Float) {
        val pedal = (state.value as? ConnectionState.Connected)?.pedal ?: return
        val binding = when (control) {
            EffectControl.KNOB_A -> effectKnobBindings(effect, pedal).first
            EffectControl.KNOB_B -> effectKnobBindings(effect, pedal).second
            EffectControl.KNOB_C -> effectKnobBindings(effect, pedal).third
            EffectControl.POST -> effect.postBinding()
            EffectControl.DELAY_SYNC -> TonexEffectParams.delaySync(pedal.delayModel())
            EffectControl.DELAY_PINGPONG -> TonexEffectParams.delayMode(pedal.delayModel())
        } ?: return
        when (control) {
            EffectControl.KNOB_A, EffectControl.KNOB_B, EffectControl.KNOB_C ->
                scheduleBindingWrite(effect to control, binding, binding.denormalize(value))
            else -> {
                val repo = repository ?: return
                viewModelScope.launch {
                    runCatching { repo.writeParameterIndex(binding.index, value.coerceIn(0f, 1f)) }
                }
            }
        }
    }

    /** Bindings dos 3 knobs da tela de detalhe, na ordem dos labels da UI. */
    private fun effectKnobBindings(
        effect: EffectSlotType,
        pedal: PedalState
    ): Triple<TonexParamBinding?, TonexParamBinding?, TonexParamBinding?> = when (effect) {
        // Labels da UI: THRESHOLD, DEPTH, RELEASE
        EffectSlotType.GATE -> Triple(
            TonexEffectParams.GATE_THRESHOLD, TonexEffectParams.GATE_DEPTH, TonexEffectParams.GATE_RELEASE
        )
        // Labels: ATTACK, THRESHOLD, MAKEUP
        EffectSlotType.CMP -> Triple(
            TonexEffectParams.COMP_ATTACK, TonexEffectParams.COMP_THRESHOLD, TonexEffectParams.COMP_MAKE_UP
        )
        // Labels: BASS FREQ, MID FREQ, TREBLE FREQ
        EffectSlotType.EQ -> Triple(
            TonexEffectParams.EQ_BASS_FREQ, TonexEffectParams.EQ_MID_FREQ, TonexEffectParams.EQ_TREBLE_FREQ
        )
        // Labels: RATE, DEPTH, MIX (indices dependem do modelo de modulacao ativo)
        EffectSlotType.MOD -> pedal.modulationModel().let { model ->
            Triple(
                TonexEffectParams.modulationRate(model),
                TonexEffectParams.modulationDepth(model),
                TonexEffectParams.modulationLevel(model)
            )
        }
        // Labels: TIME, FEEDBACK, MIX (indices dependem do modelo digital/tape)
        EffectSlotType.DLY -> pedal.delayModel().let { model ->
            Triple(
                TonexEffectParams.delayTime(model),
                TonexEffectParams.delayFeedback(model),
                TonexEffectParams.delayMix(model)
            )
        }
        // Labels: MIX, TIME, PREDELAY (indices dependem do modelo de reverb ativo)
        EffectSlotType.REV -> pedal.reverbModel().let { model ->
            Triple(
                TonexEffectParams.reverbMix(model),
                TonexEffectParams.reverbTime(model),
                TonexEffectParams.reverbPredelay(model)
            )
        }
        // Labels: MIC BLEND, RESONANCE, MIC POSITION
        EffectSlotType.CAB -> Triple(
            TonexEffectParams.VIR_BLEND, TonexEffectParams.VIR_RESO, TonexEffectParams.VIR_MIC_1_X
        )
    }

    private fun EffectSlotType.postBinding(): TonexParamBinding? = when (this) {
        EffectSlotType.GATE -> TonexEffectParams.GATE_POST
        EffectSlotType.CMP -> TonexEffectParams.COMP_POST
        EffectSlotType.EQ -> TonexEffectParams.EQ_POST
        EffectSlotType.MOD -> TonexEffectParams.MODULATION_POST
        EffectSlotType.DLY -> TonexEffectParams.DELAY_POST
        EffectSlotType.REV -> TonexEffectParams.REVERB_POSITION
        EffectSlotType.CAB -> null
    }

    private fun PedalState.reverbModel(): Int =
        parameterValue(TonexParam.REVERB_MODEL)?.toInt() ?: 0

    private fun PedalState.modulationModel(): Int =
        parameterValue(TonexParam.MODULATION_MODEL)?.toInt() ?: 0

    private fun PedalState.delayModel(): Int =
        parameterValue(TonexParam.DELAY_MODEL)?.toInt() ?: 0

    private val _menu = MutableStateFlow(MenuUiState())
    val menu: StateFlow<MenuUiState> = _menu.asStateFlow()

    fun updateMasterVolume(value: Float) {
        _menu.value = _menu.value.copy(masterVolume = value.coerceIn(0f, 1f))
    }

    fun updateA4Reference(value: Int) {
        _menu.value = _menu.value.copy(a4ReferenceOverride = value.coerceIn(430, 450))
    }

    fun connectWith(connection: PedalConnection) {
        if (_busy.value.isBusy) return
        val repo = PedalRepository(connection, viewModelScope)
        repo.attachCaptureRecorder(captureRecorder)
        attachRepository(repo)
        viewModelScope.launch {
            try {
                setBusy(localText("Conectando ao pedal...", "Connecting to pedal...", "Conectando al pedal..."))
                _error.value = null
                repo.connect()
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao conectar ao pedal", "Failed to connect to pedal", "No se pudo conectar al pedal")
                detachRepository(repo)
            } finally {
                clearBusy()
            }
        }
    }

    /**
     * Conecta ao pedal real. A [factory] e suspensa porque abrir a porta USB exige o fluxo
     * assincrono de permissao do Android (ver UsbSerialTransport.connect). Retornar null
     * significa "pedal nao encontrado" e e reportado via [error].
     */
    fun connectReal(factory: suspend () -> PedalConnection?) {
        if (_busy.value.isBusy) return
        viewModelScope.launch {
            try {
                setBusy(localText("Conectando ao pedal...", "Connecting to pedal...", "Conectando al pedal..."))
                _error.value = null
                val connection = factory()
                if (connection == null) {
                    _error.value = localText("Pedal não encontrado via USB", "Pedal not found over USB", "Pedal no encontrado por USB")
                    return@launch
                }
                val repo = PedalRepository(connection, viewModelScope)
                repo.attachCaptureRecorder(captureRecorder)
                attachRepository(repo)
                repo.connect()
                publishRepositoryState(repo.state.value)
                startStatePolling(repo)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao conectar ao pedal", "Failed to connect to pedal", "No se pudo conectar al pedal")
                repository?.let(::detachRepository)
            } finally {
                clearBusy()
            }
        }
    }

    fun selectSlot(slot: Slot) {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Trocando preset para ${slot.name}...", "Switching preset to ${slot.name}...", "Cambiando preset a ${slot.name}..."))
                repo.selectSlot(slot)
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao trocar de slot", "Failed to switch slot", "No se pudo cambiar de slot")
            } finally {
                clearBusy()
            }
        }
    }

    fun loadPresetToActiveSlot(presetId: Int) {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Carregando preset ${presetId + 1}...", "Loading preset ${presetId + 1}...", "Cargando preset ${presetId + 1}..."))
                _error.value = null
                repo.loadPresetToActiveSlot(presetId)
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao carregar preset", "Failed to load preset", "No se pudo cargar el preset")
            } finally {
                clearBusy()
            }
        }
    }

    fun switchMode(targetMode: PedalMode) {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Alterando modo para ${targetMode.name}...", "Changing mode to ${targetMode.name}...", "Cambiando modo a ${targetMode.name}..."))
                _error.value = null
                repo.switchMode(targetMode)
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao alterar modo", "Failed to change mode", "No se pudo cambiar el modo")
            } finally {
                clearBusy()
            }
        }
    }

    fun toggleBypass() {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Alternando bypass...", "Switching bypass...", "Alternando bypass..."))
                _error.value = null
                repo.toggleBypass()
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao alternar bypass", "Failed to switch bypass", "No se pudo alternar bypass")
            } finally {
                clearBusy()
            }
        }
    }

    fun toggleCabSimBypass() {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Alternando IR/Cab...", "Switching IR/Cab...", "Alternando IR/Cab..."))
                _error.value = null
                repo.toggleCabSimBypass()
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao alternar IR/Cab", "Failed to switch IR/Cab", "No se pudo alternar IR/Cab")
            } finally {
                clearBusy()
            }
        }
    }

    fun refreshState() {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Atualizando estado do pedal...", "Refreshing pedal state...", "Actualizando estado del pedal..."))
                _error.value = null
                repo.refreshState()
                publishRepositoryState(repo.state.value)
            } catch (e: Exception) {
                _error.value = e.message ?: localText("Falha ao atualizar o estado do pedal", "Failed to refresh pedal state", "No se pudo actualizar el estado del pedal")
            } finally {
                clearBusy()
            }
        }
    }

    /** Momento da ultima edicao LOCAL de cada knob (para suprimir eco 0x0309 do pedal). */
    private val knobLocalEditAt = mutableMapOf<AmpKnob, Long>()

    fun updateAmpKnob(knob: AmpKnob, value: Float) {
        Log.d("ToneXUi", "updateAmpKnob knob=$knob value=$value")
        knobLocalEditAt[knob] = System.currentTimeMillis()
        _ampKnobs.value = _ampKnobs.value.withValue(knob, value)
        scheduleKnobWrite(knob, value)
    }

    /**
     * Escreve o knob no pedal com debounce: o gesto de arrastar gera dezenas de updates,
     * mas so o valor mais recente importa. Cada knob tem seu proprio job para que mexer
     * em GAIN nao cancele uma escrita pendente de BASS.
     */
    private val knobWriteJobs = mutableMapOf<Any, Job>()

    private fun scheduleKnobWrite(knob: AmpKnob, normalized: Float) {
        val param = knob.toParam()
        scheduleBindingWrite(
            knob,
            TonexParamBinding(param.index, param.min, param.max),
            param.denormalize(normalized)
        )
    }

    /** Escrita com debounce de um valor REAL em um indice da tabela tonex_params. */
    private fun scheduleBindingWrite(key: Any, binding: TonexParamBinding, value: Float) {
        val repo = repository ?: return
        knobWriteJobs[key]?.cancel()
        knobWriteJobs[key] = viewModelScope.launch {
            delay(KNOB_WRITE_DEBOUNCE_MS)
            runCatching { repo.writeParameterIndex(binding.index, value) }
        }
    }

    fun startCapture(rootDirectory: File) {
        try {
            val recorder = captureRecorder ?: EventCaptureRecorder(rootDirectory).also {
                captureRecorder = it
            }
            val session = recorder.startSession()
            repository?.attachCaptureRecorder(recorder)
            _capture.value = CaptureUiState(
                isCapturing = true,
                currentFilePath = session.file.absolutePath,
                lastFilePath = session.file.absolutePath
            )
            _error.value = null
        } catch (e: Exception) {
            _error.value = e.message ?: localText("Falha ao iniciar a captura", "Failed to start capture", "No se pudo iniciar la captura")
        }
    }

    fun stopCapture() {
        val recorder = captureRecorder ?: return
        val file = recorder.stopSession()
        _capture.value = _capture.value.copy(
            isCapturing = false,
            currentFilePath = null,
            lastFilePath = file?.absolutePath ?: _capture.value.lastFilePath
        )
    }

    fun disconnect() {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy(localText("Desconectando...", "Disconnecting...", "Desconectando..."))
                repo.disconnect()
                publishRepositoryState(repo.state.value)
                detachRepository(repo)
                // A captura NAO para no desconectar: senao o handshake do reconnect seguinte
                // (justamente o que precisamos medir) nunca seria gravado. A captura e
                // controlada so pelos botoes Iniciar/Parar captura.
            } finally {
                clearBusy()
            }
        }
    }

    private fun setBusy(reason: String) {
        _busy.value = UiBusyState(isBusy = true, busyReason = reason)
    }

    private fun clearBusy() {
        _busy.value = UiBusyState()
    }

    private fun localText(ptBr: String, enUs: String, es: String): String =
        when (Locale.getDefault().language.lowercase(Locale.US)) {
            "pt" -> ptBr
            "es" -> es
            else -> enUs
        }

    private var parameterChangesJob: Job? = null

    private fun attachRepository(repo: PedalRepository) {
        statePollingJob?.cancel()
        repository = repo
        // Knob FISICO girado no pedal (0x0309) -> reflete no knob virtual da UI.
        parameterChangesJob?.cancel()
        parameterChangesJob = viewModelScope.launch {
            repo.parameterChanges.collect { change ->
                val knob = AmpKnob.fromParamIndex(change.paramIndex) ?: return@collect
                // Janela de supressao: o knob fisico unico do ToneX One (volume, indice 21)
                // reporta a posicao do potenciometro logo apos as nossas escritas; aplicar
                // essas notificacoes durante o arrasto travava o knob virtual de VOLUME
                // (snap-back imediato). Enquanto o usuario interage, o valor local vence.
                val lastLocal = knobLocalEditAt[knob] ?: 0L
                if (System.currentTimeMillis() - lastLocal < KNOB_ECHO_SUPPRESS_MS) return@collect
                _ampKnobs.value =
                    _ampKnobs.value.withValue(knob, knob.toParam().normalize(change.value))
            }
        }
    }

    private fun detachRepository(repo: PedalRepository) {
        if (repository === repo) repository = null
        statePollingJob?.cancel()
        statePollingJob = null
        parameterChangesJob?.cancel()
        parameterChangesJob = null
        knobWriteJobs.values.forEach(Job::cancel)
        knobWriteJobs.clear()
    }

    private fun publishRepositoryState(state: ConnectionState) {
        _state.value = state
        val connected = state as? ConnectionState.Connected ?: return
        // Knobs editados ha pouco mantem o valor local: o eco do potenciometro fisico
        // (volume) chega pelo estado do repositorio e travava o knob virtual no poll.
        val now = System.currentTimeMillis()
        val recentlyEdited = AmpKnob.entries.filterTo(mutableSetOf()) { knob ->
            now - (knobLocalEditAt[knob] ?: 0L) < KNOB_ECHO_SUPPRESS_MS
        }
        _ampKnobs.value = _ampKnobs.value.withPedalParameters(connected.pedal, skip = recentlyEdited)
        _effectChain.value = _effectChain.value.withPedalParameters(connected.pedal)
    }

    private fun startStatePolling(repo: PedalRepository) {
        statePollingJob?.cancel()
        statePollingJob = viewModelScope.launch {
            while (repository === repo) {
                delay(PEDAL_POLL_INTERVAL_MS)
                if (_busy.value.isBusy) continue
                runCatching {
                    repo.syncStateFromPedal()
                    publishRepositoryState(repo.state.value)
                }
            }
        }
    }

    override fun onCleared() {
        statePollingJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PEDAL_POLL_INTERVAL_MS = 900L
        const val KNOB_WRITE_DEBOUNCE_MS = 60L
        /** Ignora ecos 0x0309 do pedal por este intervalo apos uma edicao local do knob. */
        const val KNOB_ECHO_SUPPRESS_MS = 1500L
    }
}
