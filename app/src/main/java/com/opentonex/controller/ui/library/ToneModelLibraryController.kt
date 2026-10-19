package com.opentonex.controller.ui.library

import com.opentonex.controller.library.ToneModelEntry
import com.opentonex.controller.library.ToneModelLibrary
import com.opentonex.controller.protocol.TonexFullPreset
import com.opentonex.controller.repository.PedalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado da ultima acao da biblioteca, exibido na tela. */
sealed interface ToneModelMessage {
    data class Saved(val name: String) : ToneModelMessage
    data class Written(val name: String, val targetIndex: Int, val backupName: String) : ToneModelMessage
    data class WriteNotConfirmed(val targetIndex: Int, val backupName: String) : ToneModelMessage
    data class Imported(val name: String) : ToneModelMessage
    data class Failed(val reason: String) : ToneModelMessage
}

data class ToneModelLibraryUiState(
    val entries: List<ToneModelEntry> = emptyList(),
    val message: ToneModelMessage? = null
)

/**
 * Acoes da biblioteca de Tone Models: ler presets completos do pedal para o aparelho e
 * grava-los de volta. Toda gravacao faz antes um backup automatico do preset
 * de destino na biblioteca e confirma relendo o preset do pedal.
 */
class ToneModelLibraryController(
    private val scope: CoroutineScope,
    private val repository: () -> PedalRepository?,
    private val runBusy: (reason: String, block: suspend () -> Unit) -> Unit
) {
    private var library: ToneModelLibrary? = null
    private val _state = MutableStateFlow(ToneModelLibraryUiState())
    val state: StateFlow<ToneModelLibraryUiState> = _state.asStateFlow()

    fun attach(library: ToneModelLibrary) {
        this.library = library
        scope.launch { reload() }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Le do pedal o preset salvo [index] (ou o preset em edicao, se null) e guarda no app. */
    fun saveFromPedal(index: Int?, busyReason: String) {
        val repo = repository() ?: return
        val lib = library ?: return
        runBusy(busyReason) {
            report {
                val preset = if (index == null) {
                    repo.readFullPreset(TonexFullPreset.BANK_EDIT_BUFFER, 0)
                } else {
                    repo.readFullPreset(TonexFullPreset.BANK_STORED, index)
                }
                val entry = withContext(Dispatchers.IO) { lib.save(preset) }
                reload()
                ToneModelMessage.Saved(entry.preset.presetName)
            }
        }
    }

    /** Grava [entry] no preset salvo [targetIndex] do pedal. */
    fun writeToPedal(entry: ToneModelEntry, targetIndex: Int, busyReason: String) {
        val repo = repository() ?: return
        val lib = library ?: return
        runBusy(busyReason) {
            report {
                val current = repo.readFullPreset(TonexFullPreset.BANK_STORED, targetIndex)
                val backupName = "Backup ${targetIndex + 1} - ${current.presetName}"
                withContext(Dispatchers.IO) { lib.save(current, backupName) }
                val confirmed = repo.writeFullPreset(entry.preset.body, targetIndex)
                reload()
                if (confirmed) {
                    ToneModelMessage.Written(entry.preset.presetName, targetIndex, backupName)
                } else {
                    ToneModelMessage.WriteNotConfirmed(targetIndex, backupName)
                }
            }
        }
    }

    fun import(bytes: ByteArray, suggestedName: String?) {
        val lib = library ?: return
        scope.launch {
            report {
                val entry = withContext(Dispatchers.IO) { lib.import(bytes, suggestedName) }
                reload()
                ToneModelMessage.Imported(entry.preset.presetName)
            }
        }
    }

    suspend fun export(entry: ToneModelEntry): ByteArray? {
        val lib = library ?: return null
        return withContext(Dispatchers.IO) { runCatching { lib.export(entry) }.getOrNull() }
    }

    fun delete(entry: ToneModelEntry) {
        val lib = library ?: return
        scope.launch {
            withContext(Dispatchers.IO) { lib.delete(entry) }
            reload()
        }
    }

    private suspend fun report(action: suspend () -> ToneModelMessage) {
        val message = try {
            action()
        } catch (e: Exception) {
            ToneModelMessage.Failed(e.message ?: e.javaClass.simpleName)
        }
        _state.value = _state.value.copy(message = message)
    }

    private suspend fun reload() {
        val lib = library ?: return
        val entries = withContext(Dispatchers.IO) { lib.list() }
        _state.value = _state.value.copy(entries = entries)
    }
}
