package com.opentonex.controller.ui.presets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurfaceHigh
import com.opentonex.controller.ui.theme.ToneXSurfaceVariant

@StringRes
internal fun PresetInstrument.labelRes(): Int = when (this) {
    PresetInstrument.ELECTRIC_GUITAR -> R.string.instrument_electric_guitar
    PresetInstrument.ACOUSTIC_GUITAR -> R.string.instrument_acoustic_guitar
    PresetInstrument.ELECTRIC_BASS -> R.string.instrument_electric_bass
    PresetInstrument.ACOUSTIC_BASS -> R.string.instrument_acoustic_bass
    PresetInstrument.OTHER -> R.string.tag_other
}

@StringRes
internal fun PresetGenre.labelRes(): Int = when (this) {
    PresetGenre.ROCK -> R.string.genre_rock
    PresetGenre.METAL -> R.string.genre_metal
    PresetGenre.PUNK -> R.string.genre_punk
    PresetGenre.POP -> R.string.genre_pop
    PresetGenre.GRUNGE -> R.string.genre_grunge
    PresetGenre.BLUES -> R.string.genre_blues
    PresetGenre.JAZZ -> R.string.genre_jazz
    PresetGenre.FUNK -> R.string.genre_funk
    PresetGenre.FUSION -> R.string.genre_fusion
    PresetGenre.REGGAE -> R.string.genre_reggae
    PresetGenre.SURF -> R.string.genre_surf
    PresetGenre.COUNTRY -> R.string.genre_country
    PresetGenre.VINTAGE -> R.string.genre_vintage
    PresetGenre.OTHER -> R.string.tag_other
}

@StringRes
internal fun PresetCharacter.labelRes(): Int = when (this) {
    PresetCharacter.CLEAN -> R.string.character_clean
    PresetCharacter.DRIVE -> R.string.character_drive
    PresetCharacter.HI_GAIN -> R.string.character_hi_gain
    PresetCharacter.FUZZY -> R.string.character_fuzzy
    PresetCharacter.OTHER -> R.string.tag_other
}

@StringRes
private fun PresetSort.labelRes(): Int = when (this) {
    PresetSort.NUMBER -> R.string.sort_number
    PresetSort.NAME -> R.string.sort_name
    PresetSort.FAVORITES -> R.string.sort_favorites
}

/** Botao de filtros ao lado da busca, com a contagem de opcoes marcadas. */
@Composable
internal fun PresetFilterButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = count > 0
    Surface(
        color = if (active) ToneXAccent else ToneXSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(width = 44.dp, height = 40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = stringResource(R.string.presets_filters),
                tint = if (active) Color(0xFF111111) else ToneXOnSurfaceMuted,
                modifier = Modifier.size(20.dp)
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(Color(0xFF111111), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ToneXAccent)
                }
            }
        }
    }
}

/** Dialogo de ordenacao e filtros; as mudancas valem na hora e "Limpar" volta ao padrao. */
@Composable
internal fun PresetFilterDialog(
    filter: PresetFilter,
    onChange: (PresetFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ToneXSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.filters_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GroupTitle(R.string.filters_sort)
                ChipFlow {
                    PresetSort.entries.forEach { sort ->
                        TagChip(
                            text = stringResource(sort.labelRes()),
                            selected = filter.sort == sort,
                            onClick = { onChange(filter.copy(sort = sort)) }
                        )
                    }
                }
                ChipFlow {
                    TagChip(
                        text = stringResource(R.string.filters_favorites_only),
                        selected = filter.favoritesOnly,
                        onClick = { onChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
                    )
                }
                GroupTitle(R.string.filters_instrument)
                ChipFlow {
                    PresetInstrument.entries.forEach { item ->
                        TagChip(
                            text = stringResource(item.labelRes()),
                            selected = item in filter.instruments,
                            onClick = { onChange(filter.copy(instruments = filter.instruments.toggled(item))) }
                        )
                    }
                }
                GroupTitle(R.string.filters_genre)
                ChipFlow {
                    PresetGenre.entries.forEach { item ->
                        TagChip(
                            text = stringResource(item.labelRes()),
                            selected = item in filter.genres,
                            onClick = { onChange(filter.copy(genres = filter.genres.toggled(item))) }
                        )
                    }
                }
                GroupTitle(R.string.filters_character)
                ChipFlow {
                    PresetCharacter.entries.forEach { item ->
                        TagChip(
                            text = stringResource(item.labelRes()),
                            selected = item in filter.characters,
                            onClick = { onChange(filter.copy(characters = filter.characters.toggled(item))) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.filters_done), color = ToneXAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = { onChange(PresetFilter()) }) {
                Text(stringResource(R.string.filters_clear), color = ToneXOnSurfaceMuted)
            }
        }
    )
}

/** Escolha unica de uma tag no dialogo de edicao; tocar de novo na opcao marcada limpa. */
@Composable
internal fun <T> TagChooser(
    @StringRes title: Int,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle(title)
        ChipFlow {
            options.forEach { option ->
                TagChip(
                    text = label(option),
                    selected = option == selected,
                    onClick = { onSelect(if (option == selected) null else option) }
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(@StringRes title: Int) {
    Text(text = stringResource(title).uppercase(), style = MonoLabelStyle, color = ToneXOnSurfaceMuted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { content() }
}

@Composable
private fun TagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ToneXSurfaceHigh,
            labelColor = ToneXOnSurfaceMuted,
            selectedContainerColor = ToneXAccent,
            selectedLabelColor = Color(0xFF111111)
        )
    )
}
