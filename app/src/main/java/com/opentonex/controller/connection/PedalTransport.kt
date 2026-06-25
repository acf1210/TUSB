package com.opentonex.controller.connection

/** I/O puro de bytes, sem conhecimento de HDLC ou do protocolo ToneX. */
interface PedalTransport {
    suspend fun open()
    suspend fun write(bytes: ByteArray)
    /** Bloqueia até receber um frame HDLC completo (0x7E ... 0x7E) ou estourar o timeout. */
    suspend fun readFrame(timeoutMs: Long): ByteArray
    suspend fun close()
}

class PedalTransportTimeoutException(message: String) : Exception(message)
