package com.opentonex.controller.protocol

import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TonexMessagesTest {
    @Test fun `requestState payload starts with documented header`() {
        val payload = TonexMessages.requestStatePayload()
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x06, 0x03), payload.copyOfRange(0, 3))
    }

    @Test fun `hello payload is non-empty`() {
        org.junit.Assert.assertTrue(TonexMessages.helloPayload().isNotEmpty())
    }

    @Test fun `parse firmware reads ascii version from response`() {
        val resp = byteArrayOf(0x81.toByte(), 0x0A, 0x00) +
            "1.2.3".toByteArray(Charsets.US_ASCII)
        assertEquals("1.2.3", TonexMessages.parseFirmware(resp).version)
    }

    // Captura real do Hello (tipo 0x0306, dump de estado binario): bytes imprimiveis
    // dispersos ("LBLG" etc.) NAO devem virar string de versao. Ver docs/protocol-notes.md.
    @Test fun `parse firmware does not scrape garbage from binary state frame`() {
        val resp = byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x06, 0x03, 0x80.toByte(), 0xA0.toByte(),
            0x02, 0xB9.toByte(), 0x01, 0xB9.toByte(), 0x0E, 0x82.toByte(),
            0x4C, 0x42, 0x4C, 0x47, // "LBLG" (sem digito)
            0xB9.toByte(), 0x03, 0x00, 0x04, 0x00, 0x88.toByte()
        )
        assertEquals("ToneX One (versao nao mapeada)", TonexMessages.parseFirmware(resp).version)
    }

    @Test fun `select preset payload has documented envelope and id-phase tail`() {
        val payload = TonexMessages.selectPresetPayload(presetId = 0x0C, phase = TonexMessages.PRESET_PHASE_ARM)
        assertArrayEquals(
            byteArrayOf(
                0xB9.toByte(), 0x03, 0x81.toByte(), 0x00, 0x03,
                0x82.toByte(), 0x06, 0x00, 0x80.toByte(), 0x0B, 0x03,
                0xB9.toByte(), 0x04, 0x0B, 0x01,
                0x0C, 0x00
            ),
            payload
        )
        assertEquals(0x0300, TonexMessages.messageType(payload))
    }

    // Golden test: o payload, encapsulado em HDLC, deve bater BYTE A BYTE com os frames
    // reais do app oficial trocando preset (tonexfinal_official.pcap). Valida payload + CRC.
    @Test fun `select preset HDLC-encoded matches official app capture`() {
        fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()

        assertArrayEquals(
            hex("7E B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 0C 00 68 8E 7E"),
            HdlcCodec.encode(TonexMessages.selectPresetPayload(0x0C, TonexMessages.PRESET_PHASE_ARM))
        )
        assertArrayEquals(
            hex("7E B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 08 01 81 F8 7E"),
            HdlcCodec.encode(TonexMessages.selectPresetPayload(0x08, TonexMessages.PRESET_PHASE_COMMIT))
        )
        assertArrayEquals(
            hex("7E B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 07 00 C0 6A 7E"),
            HdlcCodec.encode(TonexMessages.selectPresetPayload(0x07, TonexMessages.PRESET_PHASE_ARM))
        )
    }

    @Test fun `select preset payloads emits arm then commit in order`() {
        val frames = TonexMessages.selectPresetPayloads(0x07)
        assertEquals(2, frames.size)
        assertEquals(TonexMessages.PRESET_PHASE_ARM, frames[0].last().toInt())
        assertEquals(TonexMessages.PRESET_PHASE_COMMIT, frames[1].last().toInt())
    }

    @Test fun `parse preset detail extracts active preset name from 0304 notification`() {
        val name = "John Mayer NDSP Arch SSS/Klon"
        val payload = byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x04, 0x03,
            0x00, 0x00,
            0xBC.toByte(), name.length.toByte()
        ) + name.toByteArray(Charsets.US_ASCII)

        assertEquals(name, TonexMessages.parsePresetNameFromDetail(payload))
    }

    @Test fun `slot change swaps response header for command header and preserves body`() {
        val raw = byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x06, 0x03, // header de tipo (5B)
            0x80.toByte(), 0xA0.toByte(), 0x02, // sufixo de RESPOSTA (3B)
            0x10, 0x20, 0x00 /* slot byte @ offset 10 */, 0x30, 0x40
        )
        val out = TonexMessages.buildSlotChangePayload(
            rawState = raw, activeSlotOffset = 10, newSlotValue = 2
        )
        assertArrayEquals(
            byteArrayOf(
                0xB9.toByte(), 0x03, 0x81.toByte(), 0x06, 0x03,
                0x82.toByte(), 0xA0.toByte(), 0x00, 0x80.toByte(), 0x0B, 0x03, // sufixo de COMANDO (6B)
                0x10, 0x20, 0x02, 0x30, 0x40
            ),
            out
        )
    }

    // Golden test: bytes reais capturados do app oficial trocando entre os slots A, B e C
    // (tonex_isolated_switch.pcap, 2026-06-25). Confirma que o COMANDO de troca de slot e
    // o StateResponse completo com o slot ativo mudado e o envelope de comando (Bug 2 resolvido).
    @Test fun `buildSetStatePayload reproduces official app slot-switch capture byte for byte`() {
        fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()

        val body = hex(
            "B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 00 00 80 40 00 01 01 BA 14 " +
                "B9 03 80 9F 80 FF 00 B9 03 2F 00 80 FF B9 03 00 80 FF 00 B9 03 00 80 FF 00 " +
                "B9 03 0F 80 FF 2F B9 03 80 FF 00 00 B9 03 00 00 80 FF B9 03 80 BF 80 BF 80 BF " +
                "B9 03 80 9F 80 FF 00 B9 03 00 80 FF 80 FF B9 03 11 11 00 B9 03 80 FF 00 00 " +
                "B9 03 00 11 00 B9 03 0A 00 0A B9 03 00 22 06 B9 03 11 00 00 B9 03 00 00 11 " +
                "B9 03 0B 0B 0B B9 03 11 22 00 B9 03 00 19 19 " +
                "BC 06 03 00 0A 00 0B 00 00 00 81 B8 01 01 00 88 F0 6F 26 43"
        )
        val rawStateSlotA = hex("B9 03 81 06 03 80 A0 02") + body

        // Os 3 comandos reais capturados (so o byte do slot ativo muda: 00, 01, 02).
        val expectedSlotA = hex(
            "B9 03 81 06 03 82 A0 00 80 0B 03 B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 " +
                "00 00 80 40 00 01 01 BA 14 B9 03 80 9F 80 FF 00 B9 03 2F 00 80 FF B9 03 00 80 FF 00 " +
                "B9 03 00 80 FF 00 B9 03 0F 80 FF 2F B9 03 80 FF 00 00 B9 03 00 00 80 FF " +
                "B9 03 80 BF 80 BF 80 BF B9 03 80 9F 80 FF 00 B9 03 00 80 FF 80 FF B9 03 11 11 00 " +
                "B9 03 80 FF 00 00 B9 03 00 11 00 B9 03 0A 00 0A B9 03 00 22 06 B9 03 11 00 00 " +
                "B9 03 00 00 11 B9 03 0B 0B 0B B9 03 11 22 00 B9 03 00 19 19 " +
                "BC 06 03 00 0A 00 0B 00 00 00 81 B8 01 01 00 88 F0 6F 26 43"
        )
        val expectedSlotBHex = hex(
            "B9 03 81 06 03 82 A0 00 80 0B 03 B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 " +
                "00 00 80 40 00 01 01 BA 14 B9 03 80 9F 80 FF 00 B9 03 2F 00 80 FF B9 03 00 80 FF 00 " +
                "B9 03 00 80 FF 00 B9 03 0F 80 FF 2F B9 03 80 FF 00 00 B9 03 00 00 80 FF " +
                "B9 03 80 BF 80 BF 80 BF B9 03 80 9F 80 FF 00 B9 03 00 80 FF 80 FF B9 03 11 11 00 " +
                "B9 03 80 FF 00 00 B9 03 00 11 00 B9 03 0A 00 0A B9 03 00 22 06 B9 03 11 00 00 " +
                "B9 03 00 00 11 B9 03 0B 0B 0B B9 03 11 22 00 B9 03 00 19 19 " +
                "BC 06 03 00 0A 00 0B 00 00 01 81 B8 01 01 00 88 F0 6F 26 43"
        )
        val expectedSlotCHex = hex(
            "B9 03 81 06 03 82 A0 00 80 0B 03 B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 " +
                "00 00 80 40 00 01 01 BA 14 B9 03 80 9F 80 FF 00 B9 03 2F 00 80 FF B9 03 00 80 FF 00 " +
                "B9 03 00 80 FF 00 B9 03 0F 80 FF 2F B9 03 80 FF 00 00 B9 03 00 00 80 FF " +
                "B9 03 80 BF 80 BF 80 BF B9 03 80 9F 80 FF 00 B9 03 00 80 FF 80 FF B9 03 11 11 00 " +
                "B9 03 80 FF 00 00 B9 03 00 11 00 B9 03 0A 00 0A B9 03 00 22 06 B9 03 11 00 00 " +
                "B9 03 00 00 11 B9 03 0B 0B 0B B9 03 11 22 00 B9 03 00 19 19 " +
                "BC 06 03 00 0A 00 0B 00 00 02 81 B8 01 01 00 88 F0 6F 26 43"
        )

        val fieldsOffset = 22
        assertArrayEquals(expectedSlotA, TonexMessages.buildSetStatePayload(rawStateSlotA, fieldsOffset, Slot.A))
        assertArrayEquals(expectedSlotBHex, TonexMessages.buildSetStatePayload(rawStateSlotA, fieldsOffset, Slot.B))
        assertArrayEquals(expectedSlotCHex, TonexMessages.buildSetStatePayload(rawStateSlotA, fieldsOffset, Slot.C))
    }
}

private fun encodeColorItem(r: Int, g: Int, b: Int): ByteArray {
    fun component(v: Int): ByteArray =
        if (v >= 0x80) byteArrayOf(0x80.toByte(), v.toByte()) else byteArrayOf(v.toByte())
    return byteArrayOf(0xB9.toByte(), 3) + component(r) + component(g) + component(b)
}

/** Estrutura calibrada contra captura real do pedal (Fase 2, Tarefa 8). */
private fun syntheticStatePayload(activeSlotByte: Byte = 1): ByteArray {
    val header = ByteArray(22) // header bruto do StateResponse, ignorado pelo parser
    val trim = TaggedValue.encodeFloat(1.5f)
    val flags = byteArrayOf(0x01, 0x00, 0x00) // cabSimBypass, tuningMode, campo desconhecido
    val colors = byteArrayOf(0xBA.toByte(), 3) +
        encodeColorItem(255, 0, 0) + encodeColorItem(0, 255, 0) + encodeColorItem(0, 0, 255)
    val slotAssignment = byteArrayOf(0xBC.toByte(), 6, 0x0C, 0x00, 0x08, 0x00, 0x07, 0x00)
    val preActiveSlotByte = byteArrayOf(0)
    val a4 = TaggedValue.encodeU16(440, tag = 0x81)
    val directMonitor = byteArrayOf(0)
    val tempoSource = byteArrayOf(0)
    val tempo = TaggedValue.encodeFloat(120.0f)

    return header + trim + flags + colors + slotAssignment +
        preActiveSlotByte + byteArrayOf(activeSlotByte) + a4 + directMonitor + tempoSource + tempo
}

class TonexMessagesStateTest {
    @Test fun `parseState decodes documented fields from synthetic payload`() {
        val payload = syntheticStatePayload(activeSlotByte = 1)

        val state = TonexMessages.parseState(payload, fieldsOffset = 22)

        assertEquals(1.5f, state.inputTrim)
        assertEquals(Slot.B, state.activeSlot)
        assertEquals(440, state.a4Reference)
        assertEquals(120, state.tempo)
        assertEquals(3, state.slots.size)
        assertEquals(Rgb(255, 0, 0), state.slots[0].color)
        assertEquals(Rgb(0, 255, 0), state.slots[1].color)
        assertEquals(Rgb(0, 0, 255), state.slots[2].color)
        assertArrayEquals(payload, state.rawState)
        assertEquals(listOf(0x0C, 0x08, 0x07), state.presetIds)
    }

    @Test fun `presetIdForSlot resolves the preset library id assigned to each slot`() {
        val state = TonexMessages.parseState(syntheticStatePayload(activeSlotByte = 0), fieldsOffset = 22)

        assertEquals(0x0C, TonexMessages.presetIdForSlot(state, Slot.A))
        assertEquals(0x08, TonexMessages.presetIdForSlot(state, Slot.B))
        assertEquals(0x07, TonexMessages.presetIdForSlot(state, Slot.C))
    }

    @Test fun `withActivePresetName rewrites only the active slot label`() {
        val original = TonexMessages.parseState(syntheticStatePayload(activeSlotByte = 1), fieldsOffset = 22)

        val updated = original.withActivePresetName("Crunch Deluxe")

        assertEquals("Preset A", updated.slots[0].name)
        assertEquals("Crunch Deluxe", updated.slots[1].name)
        assertEquals("Preset C", updated.slots[2].name)
    }

    @Test fun `buildSetStatePayload mutates only the active slot byte`() {
        val payload = syntheticStatePayload(activeSlotByte = 1)

        val updated = TonexMessages.buildSetStatePayload(payload, fieldsOffset = 22, newSlot = Slot.C)

        // O comando troca o header de RESPOSTA (8B) por um header de COMANDO (11B) e
        // preserva o corpo, so com o byte do slot ativo mutado (ver Bug 2 resolvido).
        val activeSlotOffset = TonexMessages.activeSlotOffset(payload, fieldsOffset = 22)
        val expectedBody = payload.copyOfRange(8, payload.size)
        expectedBody[activeSlotOffset - 8] = 2
        val expectedSuffix = byteArrayOf(0x82.toByte(), 0xA0.toByte(), 0x00, 0x80.toByte(), 0x0B, 0x03)
        assertArrayEquals(payload.copyOfRange(0, 5) + expectedSuffix + expectedBody, updated)
        assertEquals(payload.size + 3, updated.size)
    }

    @Test fun `parseState reads active slot from the byte after the constant zero marker`() {
        val payload = syntheticStatePayload(activeSlotByte = 2)

        val state = TonexMessages.parseState(payload, fieldsOffset = 22)

        assertEquals(Slot.C, state.activeSlot)
    }
}
