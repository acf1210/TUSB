package com.opentonex.controller.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentonex.controller.R
import com.opentonex.controller.update.UpdateViewModel
import com.opentonex.controller.update.AvailableUpdate
import com.opentonex.controller.update.UpdateCheckResult
import com.opentonex.controller.update.UpdateUiState

/** Dialogo de nova versao: baixar o APK, lembrar depois ou ignorar esta versao. */
@Composable
fun UpdateDialog(
    update: AvailableUpdate,
    currentVersion: String,
    onLater: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_title, update.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.update_installed, currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text(update.notes, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = stringResource(R.string.update_install_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openLink(context, update.apkUrl ?: update.releaseUrl)
                onLater()
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            }
        }
    )
}

/** Cartao do Menu: versao instalada, checagem manual e liga/desliga da automatica. */
@Composable
fun UpdateSettingsCard(
    state: UpdateUiState,
    onCheckNow: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_section), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.update_installed, state.currentVersion), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.update_auto),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = state.autoCheckEnabled, onCheckedChange = onAutoCheckChange)
            }
            Button(onClick = onCheckNow, enabled = !state.checking) {
                Text(stringResource(if (state.checking) R.string.update_checking else R.string.update_check_now))
            }
            state.lastResult?.let { result ->
                Text(
                    text = when (result) {
                        UpdateCheckResult.UpToDate -> stringResource(R.string.update_up_to_date)
                        is UpdateCheckResult.Available -> stringResource(R.string.update_available, result.version)
                        is UpdateCheckResult.Failed -> stringResource(R.string.update_failed, result.reason)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result is UpdateCheckResult.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Mesma instancia na raiz do app e no Menu: o ViewModel fica preso a Activity, nao a rota. */
@Composable
fun activityUpdateViewModel(): UpdateViewModel {
    var context = LocalContext.current
    while (context !is ComponentActivity && context is android.content.ContextWrapper) context = context.baseContext
    return viewModel(viewModelStoreOwner = context as ComponentActivity)
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sem navegador instalado: nada a abrir.
    }
}
