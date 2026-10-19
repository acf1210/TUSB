package com.opentonex.controller.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Resultado da ultima checagem manual, mostrado no Menu. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val version: String) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

data class UpdateUiState(
    val currentVersion: String = "",
    val autoCheckEnabled: Boolean = true,
    val checking: Boolean = false,
    /** Atualizacao a oferecer no dialogo; null quando nao ha ou o usuario adiou/ignorou. */
    val pending: AvailableUpdate? = null,
    val lastResult: UpdateCheckResult? = null
)

/**
 * Checagem de atualizacao contra a ultima release do GitHub. Ao abrir o app, checa sozinha
 * no maximo a cada 12 h (se ligada); o Menu permite checar na hora e desligar a automatica.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val checker = UpdateChecker()
    private val _state = MutableStateFlow(
        UpdateUiState(
            currentVersion = installedVersion(application),
            autoCheckEnabled = prefs.getBoolean(KEY_AUTO, true)
        )
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Checagem automatica da abertura do app: respeita o liga/desliga e o intervalo de 12 h. */
    fun checkIfDue() {
        if (_state.value.autoCheckEnabled && isAutoCheckDue(prefs.getLong(KEY_LAST_CHECK, 0L), System.currentTimeMillis())) {
            check(manual = false)
        }
    }

    fun checkNow() = check(manual = true)

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _state.value = _state.value.copy(autoCheckEnabled = enabled)
    }

    /** Fecha o dialogo; a mesma versao volta a ser oferecida na proxima checagem. */
    fun postpone() {
        _state.value = _state.value.copy(pending = null)
    }

    /** Nao oferece mais esta versao automaticamente (a checagem manual ainda mostra). */
    fun skip(update: AvailableUpdate) {
        prefs.edit().putString(KEY_SKIPPED, update.version).apply()
        _state.value = _state.value.copy(pending = null)
    }

    private fun check(manual: Boolean) {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true)
        viewModelScope.launch {
            val result = try {
                val latest = checker.latest(Locale.getDefault().language.lowercase(Locale.US))
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                val newer = AppVersion.isNewer(latest.version, _state.value.currentVersion)
                val skipped = !manual && latest.version == prefs.getString(KEY_SKIPPED, null)
                if (newer && !skipped) _state.value = _state.value.copy(pending = latest)
                if (newer) UpdateCheckResult.Available(latest.version) else UpdateCheckResult.UpToDate
            } catch (e: Exception) {
                android.util.Log.w("TUSBUpdate", "checagem de atualizacao falhou (manual=$manual)", e)
                UpdateCheckResult.Failed(e.message ?: e.javaClass.simpleName)
            }
            // A checagem automatica e silenciosa: so a manual mostra "atualizado"/erro no Menu.
            _state.value = _state.value.copy(checking = false, lastResult = if (manual) result else _state.value.lastResult)
        }
    }

    private companion object {
        const val PREFS = "tusb_updates"
        const val KEY_AUTO = "auto_check"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_SKIPPED = "skipped_version"

        fun installedVersion(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}
