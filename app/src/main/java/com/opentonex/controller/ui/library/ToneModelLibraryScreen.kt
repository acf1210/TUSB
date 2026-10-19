package com.opentonex.controller.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.domain.LibraryPreset
import com.opentonex.controller.library.ToneModelEntry
import com.opentonex.controller.library.ToneModelLibrary
import com.opentonex.controller.protocol.TonexFullPreset
import com.opentonex.controller.ui.components.TusbSegmentedRow
import com.opentonex.controller.ui.components.tusbBackground
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXError
import com.opentonex.controller.ui.theme.ToneXGreen
import com.opentonex.controller.ui.theme.ToneXOnSurface
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurface
import com.opentonex.controller.ui.theme.ToneXSurfaceVariant
import kotlinx.coroutines.launch

/**
 * Biblioteca de Tone Models: guarda no aparelho presets completos lidos do pedal (com o
 * Tone Model), exporta/importa arquivos `.tusbtone` e grava um preset guardado em qualquer
 * posicao do pedal, com backup automatico do destino.
 */
@Composable
fun ToneModelLibraryScreen(
    state: ToneModelLibraryUiState,
    pedalPresets: List<LibraryPreset>,
    isBusy: Boolean,
    busyReason: String?,
    controller: ToneModelLibraryController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var writeTarget by remember { mutableStateOf<ToneModelEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ToneModelEntry?>(null) }
    var exportTarget by remember { mutableStateOf<ToneModelEntry?>(null) }
    val readingText = stringResource(R.string.tone_models_reading)
    val writingText = stringResource(R.string.tone_models_writing)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        val name = uri.lastPathSegment?.substringAfterLast('/')
        if (bytes != null) controller.import(bytes, name)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ToneModelLibrary.MIME_TYPE)
    ) { uri ->
        val entry = exportTarget
        exportTarget = null
        if (uri == null || entry == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = controller.export(entry) ?: return@launch
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
    }

    Box(modifier = modifier.fillMaxSize().tusbBackground(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 620.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = ToneXOnSurface)
                }
                Text(
                    text = stringResource(R.string.tone_models_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ToneXOnSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !isBusy) {
                    Icon(Icons.Filled.FileUpload, stringResource(R.string.tone_models_import), tint = ToneXOnSurface)
                }
            }
            TusbSegmentedRow(
                options = listOf(
                    stringResource(R.string.tone_models_tab_library),
                    stringResource(R.string.tone_models_tab_pedal)
                ),
                selectedIndex = tab,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                onSelect = { tab = it }
            )
            StatusLine(state.message, busyReason.takeIf { isBusy }, controller::clearMessage)
            if (tab == 0) {
                LibraryList(
                    entries = state.entries,
                    isBusy = isBusy,
                    onWrite = { writeTarget = it },
                    onExport = { entry ->
                        exportTarget = entry
                        exportLauncher.launch(entry.fileName)
                    },
                    onDelete = { deleteTarget = it }
                )
            } else {
                PedalList(
                    presets = pedalPresets,
                    isBusy = isBusy,
                    onSaveCurrent = { controller.saveFromPedal(null, readingText) },
                    onSave = { index -> controller.saveFromPedal(index, readingText) }
                )
            }
        }
    }

    writeTarget?.let { entry ->
        WriteDialog(
            entry = entry,
            pedalPresets = pedalPresets,
            onConfirm = { target ->
                writeTarget = null
                controller.writeToPedal(entry, target, writingText)
            },
            onDismiss = { writeTarget = null }
        )
    }
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.tone_models_delete_title)) },
            text = { Text(entry.preset.presetName) },
            confirmButton = {
                TextButton(onClick = { controller.delete(entry); deleteTarget = null }) {
                    Text(stringResource(R.string.tone_models_delete), color = ToneXError)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun StatusLine(message: ToneModelMessage?, busyReason: String?, onDismiss: () -> Unit) {
    val (text, color) = when {
        busyReason != null -> busyReason to ToneXAccent
        message == null -> return
        else -> message.text() to if (message is ToneModelMessage.Failed || message is ToneModelMessage.WriteNotConfirmed) ToneXError else ToneXGreen
    }
    Surface(
        color = ToneXSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onDismiss)
    ) {
        Text(text = text, color = color, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ToneModelMessage.text(): String = when (this) {
    is ToneModelMessage.Saved -> stringResource(R.string.tone_models_saved, name)
    is ToneModelMessage.Imported -> stringResource(R.string.tone_models_imported, name)
    is ToneModelMessage.Written -> stringResource(R.string.tone_models_written, name, targetIndex + 1, backupName)
    is ToneModelMessage.WriteNotConfirmed -> stringResource(R.string.tone_models_write_unconfirmed, targetIndex + 1, backupName)
    is ToneModelMessage.Failed -> stringResource(R.string.tone_models_failed, reason)
}

@Composable
private fun LibraryList(
    entries: List<ToneModelEntry>,
    isBusy: Boolean,
    onWrite: (ToneModelEntry) -> Unit,
    onExport: (ToneModelEntry) -> Unit,
    onDelete: (ToneModelEntry) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tone_models_empty),
                    fontSize = 13.sp,
                    color = ToneXOnSurfaceMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
        }
        items(entries, key = { it.fileName }) { entry ->
            Surface(color = ToneXSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.preset.presetName, color = ToneXOnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.preset.toneModelName, color = ToneXAccent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = listOfNotNull(entry.preset.toneModelCreator, entry.preset.toneModelDate, entry.fileName).joinToString(" · "),
                            color = ToneXOnSurfaceMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RowAction(Icons.Filled.Upload, stringResource(R.string.tone_models_write), !isBusy) { onWrite(entry) }
                    RowAction(Icons.Filled.FileDownload, stringResource(R.string.tone_models_export), !isBusy) { onExport(entry) }
                    RowAction(Icons.Filled.Delete, stringResource(R.string.tone_models_delete), !isBusy) { onDelete(entry) }
                }
            }
        }
    }
}

@Composable
private fun PedalList(
    presets: List<LibraryPreset>,
    isBusy: Boolean,
    onSaveCurrent: () -> Unit,
    onSave: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Button(
                onClick = onSaveCurrent,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = ToneXAccent, contentColor = Color(0xFF111111)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.tone_models_save_current), fontWeight = FontWeight.SemiBold) }
            Text(
                text = stringResource(R.string.tone_models_pedal_hint),
                color = ToneXOnSurfaceMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        items(pedalSlots(presets), key = { it.index }) { preset ->
            Surface(color = ToneXSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${preset.index + 1}", color = ToneXOnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.widthIn(min = 28.dp))
                    Text(preset.name, color = ToneXOnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RowAction(Icons.Filled.SaveAlt, stringResource(R.string.tone_models_save), !isBusy) { onSave(preset.index) }
                }
            }
        }
    }
}

@Composable
private fun WriteDialog(
    entry: ToneModelEntry,
    pedalPresets: List<LibraryPreset>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tone_models_write_title, entry.preset.presetName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tone_models_write_warning), fontSize = 13.sp, color = ToneXAccent)
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(pedalSlots(pedalPresets), key = { it.index }) { preset ->
                        val selected = target == preset.index
                        OutlinedButton(
                            onClick = { target = preset.index },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) ToneXAccent else Color.Transparent,
                                contentColor = if (selected) Color(0xFF111111) else ToneXOnSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${preset.index + 1}. ${preset.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onConfirm) }, enabled = target != null) {
                Text(stringResource(R.string.tone_models_write))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RowAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, label, tint = if (enabled) ToneXOnSurface else ToneXOnSurfaceMuted, modifier = Modifier.size(22.dp))
    }
}

/** Os 20 presets salvos do pedal, mesmo antes de o estado trazer os nomes. */
private fun pedalSlots(presets: List<LibraryPreset>): List<LibraryPreset> =
    (0 until TonexFullPreset.STORED_PRESET_COUNT).map { index ->
        presets.getOrNull(index) ?: LibraryPreset(index = index, name = "Preset ${index + 1}", color = presets.firstOrNull()?.color ?: com.opentonex.controller.domain.Rgb(0, 0, 0))
    }
