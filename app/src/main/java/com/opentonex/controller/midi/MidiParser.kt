package com.opentonex.controller.midi

/**
 * Converte o stream de bytes MIDI 1.0 em mensagens tipadas. Mantem estado entre chamadas
 * porque pacotes BLE/USB podem fragmentar uma mensagem (status num pacote, data no
 * seguinte) e porque running status omite o status byte em mensagens repetidas.
 *
 * Tolerante a lixo: data bytes sem status, SysEx e mensagens nao suportadas sao
 * descartados sem lancar excecao.
 */
class MidiParser {
    private var runningStatus = 0
    private var firstDataByte = -1
    private var inSysEx = false

    fun parse(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size): List<MidiMessage> {
        val messages = mutableListOf<MidiMessage>()
        for (i in offset until offset + count) {
            val b = bytes[i].toInt() and 0xFF
            when {
                // Realtime (0xF8..0xFF): pode aparecer no MEIO de outra mensagem e nao
                // afeta running status nem o data byte pendente.
                b >= 0xF8 -> Unit
                b == 0xF0 -> { inSysEx = true; runningStatus = 0; firstDataByte = -1 }
                b == 0xF7 -> inSysEx = false
                // System common (0xF1..0xF6) cancela running status.
                b >= 0xF0 -> { runningStatus = 0; firstDataByte = -1 }
                b >= 0x80 -> { runningStatus = b; firstDataByte = -1; inSysEx = false }
                inSysEx -> Unit
                else -> consumeDataByte(b, messages)
            }
        }
        return messages
    }

    private fun consumeDataByte(data: Int, messages: MutableList<MidiMessage>) {
        val channel = runningStatus and 0x0F
        when (runningStatus and 0xF0) {
            0xC0 -> messages += MidiMessage.ProgramChange(channel, data)
            0xB0 -> if (firstDataByte < 0) {
                firstDataByte = data
            } else {
                messages += MidiMessage.ControlChange(channel, firstDataByte, data)
                firstDataByte = -1
            }
            // Note on/off, aftertouch e pitch bend tem 2 data bytes: consome sem emitir.
            0x80, 0x90, 0xA0, 0xE0 -> firstDataByte = if (firstDataByte < 0) data else -1
            // Channel pressure (0xD0) tem 1 data byte: consome sem emitir.
            0xD0 -> Unit
            // Sem status corrente: byte orfao, descarta.
            else -> Unit
        }
    }
}
