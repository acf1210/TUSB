package com.opentonex.controller.ui.presets

import com.opentonex.controller.domain.LibraryPreset
import com.opentonex.controller.domain.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetFilterTest {
    private val presets = listOf(
        LibraryPreset(0, "Clean Sparkle", Rgb(0, 0, 0)),
        LibraryPreset(1, "crunch plexi", Rgb(0, 0, 0)),
        LibraryPreset(2, "Bass Drive", Rgb(0, 0, 0)),
        LibraryPreset(3, "Alpha Lead", Rgb(0, 0, 0))
    )

    private val tags = mapOf(
        0 to PresetCustomization(
            favorite = true,
            instrument = PresetInstrument.ELECTRIC_GUITAR,
            genre = PresetGenre.POP,
            character = PresetCharacter.CLEAN
        ),
        1 to PresetCustomization(
            instrument = PresetInstrument.ELECTRIC_GUITAR,
            genre = PresetGenre.ROCK,
            character = PresetCharacter.DRIVE
        ),
        2 to PresetCustomization(
            favorite = true,
            instrument = PresetInstrument.ELECTRIC_BASS,
            character = PresetCharacter.DRIVE
        )
    )

    private fun indexes(filter: PresetFilter, query: String = "") =
        presets.applyPresetFilter(filter, tags, query).map { it.index }

    @Test
    fun defaultFilterKeepsEveryPresetInNumericOrder() {
        assertEquals(listOf(0, 1, 2, 3), indexes(PresetFilter()))
    }

    @Test
    fun favoritesOnlyDropsPresetsWithoutStar() {
        assertEquals(listOf(0, 2), indexes(PresetFilter(favoritesOnly = true)))
    }

    @Test
    fun optionsInsideOneGroupAreOr() {
        val filter = PresetFilter(
            instruments = setOf(PresetInstrument.ELECTRIC_BASS, PresetInstrument.ACOUSTIC_BASS)
        )
        assertEquals(listOf(2), indexes(filter))
        val both = PresetFilter(
            instruments = setOf(PresetInstrument.ELECTRIC_BASS, PresetInstrument.ELECTRIC_GUITAR)
        )
        assertEquals(listOf(0, 1, 2), indexes(both))
    }

    @Test
    fun differentGroupsAreAnd() {
        val filter = PresetFilter(
            instruments = setOf(PresetInstrument.ELECTRIC_GUITAR),
            characters = setOf(PresetCharacter.DRIVE)
        )
        assertEquals(listOf(1), indexes(filter))
    }

    @Test
    fun untaggedPresetNeverPassesAnActiveGroup() {
        // Preset 3 nao tem personalizacao nenhuma; preset 2 nao tem genero.
        assertEquals(listOf(0, 1), indexes(PresetFilter(genres = PresetGenre.entries.toSet())))
    }

    @Test
    fun searchMatchesCustomNicknameAndOriginalName() {
        val renamed = tags + (3 to PresetCustomization(name = "Solo Boost"))
        val byNickname = presets.applyPresetFilter(PresetFilter(), renamed, "solo").map { it.index }
        val byOriginal = presets.applyPresetFilter(PresetFilter(), renamed, "alpha").map { it.index }
        assertEquals(listOf(3), byNickname)
        assertEquals(listOf(3), byOriginal)
    }

    @Test
    fun searchMatchesToneModelName() {
        val models = mapOf(1 to "Plexi Lead 100", 2 to "Clean Twin")
        val found = presets.applyPresetFilter(PresetFilter(), tags, "plexi", models).map { it.index }
        assertEquals(listOf(1), found)
    }

    @Test
    fun displayNameIsNicknameThenToneModelThenGeneric() {
        val models = mapOf(0 to "Plexi Lead 100", 1 to "Clean Twin")
        val renamed = mapOf(1 to PresetCustomization(name = "Solo"))
        assertEquals("Plexi Lead 100", presetDisplayName(presets[0], renamed[0], models))
        assertEquals("Solo", presetDisplayName(presets[1], renamed[1], models))
        assertEquals(presets[2].name, presetDisplayName(presets[2], null, models))
    }

    @Test
    fun nameSortIgnoresCaseAndUsesNickname() {
        val renamed = tags + (3 to PresetCustomization(name = "zzz last"))
        val sorted = presets.applyPresetFilter(PresetFilter(sort = PresetSort.NAME), renamed, "")
        assertEquals(listOf(2, 0, 1, 3), sorted.map { it.index })
    }

    @Test
    fun favoritesSortPutsStarsFirstThenNumericOrder() {
        assertEquals(listOf(0, 2, 1, 3), indexes(PresetFilter(sort = PresetSort.FAVORITES)))
    }

    @Test
    fun activeCountCountsFilterOptionsButNotSort() {
        val filter = PresetFilter(
            sort = PresetSort.NAME,
            favoritesOnly = true,
            instruments = setOf(PresetInstrument.OTHER),
            genres = setOf(PresetGenre.ROCK, PresetGenre.JAZZ)
        )
        assertEquals(4, filter.activeCount)
        assertEquals(0, PresetFilter(sort = PresetSort.FAVORITES).activeCount)
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val filter = PresetFilter(
            sort = PresetSort.FAVORITES,
            favoritesOnly = true,
            instruments = setOf(PresetInstrument.ACOUSTIC_GUITAR),
            genres = setOf(PresetGenre.BLUES, PresetGenre.FUNK),
            characters = setOf(PresetCharacter.HI_GAIN)
        )
        assertEquals(filter, PresetFilter.decode(filter.encode()))
        assertEquals(PresetFilter(), PresetFilter.decode(PresetFilter().encode()))
    }

    @Test
    fun decodeFallsBackToDefaultOnGarbageAndKeepsKnownValues() {
        assertEquals(PresetFilter(), PresetFilter.decode(null))
        assertEquals(PresetFilter(), PresetFilter.decode("not;enough"))
        val partial = PresetFilter.decode("BOGUS;true;ELECTRIC_BASS,NOPE;;")
        assertEquals(PresetSort.NUMBER, partial.sort)
        assertTrue(partial.favoritesOnly)
        assertEquals(setOf(PresetInstrument.ELECTRIC_BASS), partial.instruments)
    }

    @Test
    fun toggledAddsAndRemoves() {
        val once = emptySet<PresetGenre>().toggled(PresetGenre.JAZZ)
        assertEquals(setOf(PresetGenre.JAZZ), once)
        assertTrue(once.toggled(PresetGenre.JAZZ).isEmpty())
    }

    @Test
    fun customizationIsEmptyOnlyWhenNothingIsSet() {
        assertTrue(PresetCustomization().isEmpty)
        assertEquals(false, PresetCustomization(favorite = true).isEmpty)
        assertEquals(false, PresetCustomization(genre = PresetGenre.OTHER).isEmpty)
    }
}
