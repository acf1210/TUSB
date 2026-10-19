package com.opentonex.controller.library

import com.opentonex.controller.connection.SimulatedFullPreset
import com.opentonex.controller.protocol.FullPresetParseException
import com.opentonex.controller.protocol.TonexFullPreset
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneModelLibraryTest {
    private val directory: File = Files.createTempDirectory("tone-models").toFile()
    private val library = ToneModelLibrary(directory)
    private val preset = TonexFullPreset.parse(
        SimulatedFullPreset.body(bank = 1, index = 4, presetName = "MAD/FUZZ", toneModelName = "Demo Fuzz")
    )

    @After
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `saved presets are listed with their metadata`() {
        library.save(preset)
        val entries = library.list()
        assertEquals(1, entries.size)
        assertEquals("MAD/FUZZ", entries[0].preset.presetName)
        assertEquals("Demo Fuzz", entries[0].preset.toneModelName)
        assertTrue(entries[0].fileName.endsWith(ToneModelLibrary.EXTENSION))
        assertTrue("nome de arquivo sem barra", '/' !in entries[0].fileName)
    }

    @Test
    fun `saving the same name twice keeps both files`() {
        library.save(preset)
        library.save(preset)
        assertEquals(2, library.list().size)
    }

    @Test
    fun `export and import round trip the exact pedal body`() {
        val exported = library.export(library.save(preset))
        val imported = ToneModelLibrary(Files.createTempDirectory("other").toFile())
            .import(exported, "shared.tusbtone")
        assertArrayEquals(preset.body, imported.preset.body)
        assertEquals("shared.tusbtone", imported.fileName)
    }

    @Test(expected = FullPresetParseException::class)
    fun `import rejects files that are not tusbtone`() {
        library.import("29832.npTdbaOz1v".toByteArray(), "model.txp")
    }

    @Test(expected = FullPresetParseException::class)
    fun `import rejects a corrupted body`() {
        val bytes = ToneModelLibrary.encode(preset.body)
        library.import(bytes.copyOfRange(0, bytes.size - 1), null)
    }

    @Test
    fun `delete removes the file`() {
        val entry = library.save(preset)
        assertTrue(library.delete(entry))
        assertTrue(library.list().isEmpty())
    }
}
