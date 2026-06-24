package com.opentonex.controller.protocol

import com.opentonex.controller.domain.FirmwareInfo

object TonexMessages {
    /** Header documentado do request de estado: 0x81 0x06 0x03. */
    fun requestStatePayload(): ByteArray = byteArrayOf(0x81.toByte(), 0x06, 0x03)

    /** Mensagem inicial de handshake. Bytes refinados contra captura real na Fase 2. */
    fun helloPayload(): ByteArray = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x03, 0x00)

    /** Extrai a versao ASCII imprimivel da resposta de Hello. */
    fun parseFirmware(response: ByteArray): FirmwareInfo {
        val version = response
            .filter { it.toInt() in 0x20..0x7E }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
        return FirmwareInfo(version = version.ifEmpty { "desconhecida" })
    }
}
