package com.opentonex.controller.midi

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Composicao de todo o subsistema MIDI: store (mapeamento persistido), dispatcher
 * (mensagem -> acao) e input manager (USB/BLE). Criado uma vez no MainActivity e
 * repassado a UI. E o unico objeto que a UI da aba MIDI conhece.
 */
class MidiController(
    context: Context,
    handler: MidiActionHandler
) {
    private val store = MidiMappingStore(
        SharedPreferencesKeyValueStore(
            context.getSharedPreferences("midi", Context.MODE_PRIVATE)
        )
    )

    private val _pcSelectsBank = MutableStateFlow(store.pcSelectsBank())
    val pcSelectsBank: StateFlow<Boolean> = _pcSelectsBank.asStateFlow()

    val dispatcher = MidiCommandDispatcher(
        handler = handler,
        mappingProvider = { store.mapping.value },
        pcSelectsBank = { _pcSelectsBank.value }
    )

    private val inputManager = MidiInputManager(context) { messages ->
        messages.forEach(dispatcher::dispatch)
    }

    init {
        dispatcher.onLearned = { action, cc -> store.learn(action, cc) }
    }

    val mapping: StateFlow<MidiMapping> = store.mapping
    val devices: StateFlow<List<MidiDeviceUi>> = inputManager.devices
    val connectionState: StateFlow<MidiConnectionState> = inputManager.state
    val learnTarget: StateFlow<MidiAction?> = dispatcher.learnTarget
    val lastMessage: StateFlow<MidiMessage?> = dispatcher.lastMessage
    val isMidiSupported: Boolean get() = inputManager.isMidiSupported

    fun startBleScan() = inputManager.startBleScan()
    fun stopBleScan() = inputManager.stopBleScan()
    fun refreshUsbDevices() = inputManager.refreshUsbDevices()
    fun connect(device: MidiDeviceUi) = inputManager.connect(device)
    fun disconnect() = inputManager.disconnect()
    fun startLearn(action: MidiAction) = dispatcher.startLearn(action)
    fun cancelLearn() = dispatcher.cancelLearn()
    fun resetMapping() = store.reset()

    fun setPcSelectsBank(enabled: Boolean) {
        store.setPcSelectsBank(enabled)
        _pcSelectsBank.value = enabled
    }
    fun release() = inputManager.release()
}
