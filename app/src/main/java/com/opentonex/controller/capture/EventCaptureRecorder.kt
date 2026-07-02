package com.opentonex.controller.capture

import com.opentonex.controller.connection.PedalRuntimeEvent
import java.io.File

data class CaptureSession(
    val sessionId: String,
    val file: File
)

class EventCaptureRecorder(
    private val rootDirectory: File,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private var activeSession: CaptureSession? = null
    private var lastSessionFile: File? = null

    @Synchronized
    fun startSession(): CaptureSession {
        activeSession?.let { return it }
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }
        val sessionTimestamp = nowMs()
        val session = CaptureSession(
            sessionId = "tonex-$sessionTimestamp",
            file = File(rootDirectory, "tonex-session-$sessionTimestamp.jsonl")
        )
        session.file.parentFile?.mkdirs()
        if (!session.file.exists()) {
            session.file.createNewFile()
        }
        activeSession = session
        lastSessionFile = session.file
        return session
    }

    @Synchronized
    fun stopSession(): File? {
        val file = activeSession?.file ?: lastSessionFile
        activeSession = null
        return file
    }

    @Synchronized
    fun currentSession(): CaptureSession? = activeSession

    @Synchronized
    fun lastFile(): File? = lastSessionFile

    @Synchronized
    fun record(event: PedalRuntimeEvent) {
        val session = activeSession ?: return
        val line = toJson(event = event, sessionId = session.sessionId, timestamp = nowMs())
        session.file.appendText("$line\n")
    }

    private fun toJson(event: PedalRuntimeEvent, sessionId: String, timestamp: Long): String {
        val payload = linkedMapOf<String, Any?>(
            "timestamp" to timestamp,
            "sessionId" to sessionId
        )

        when (event) {
            is PedalRuntimeEvent.LocalAction -> {
                payload["source"] = "local_action"
                payload["messageType"] = null
                payload["eventKind"] = event.action
                payload["payloadHex"] = null
                payload["parsed"] = event.details
                payload["notes"] = event.notes
            }
            is PedalRuntimeEvent.RequestSent -> {
                payload["source"] = "request"
                payload["messageType"] = event.messageType?.toHexType()
                payload["eventKind"] = event.requestKind
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = mapOf("requestKind" to event.requestKind)
                payload["notes"] = null
            }
            is PedalRuntimeEvent.HelloResponseReceived -> {
                payload["source"] = "response"
                payload["messageType"] = event.messageType.toHexType()
                payload["eventKind"] = "hello_response"
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = mapOf("firmwareVersion" to event.firmwareVersion)
                payload["notes"] = null
            }
            is PedalRuntimeEvent.StateReceived -> {
                payload["source"] = "response"
                payload["messageType"] = event.messageType.toHexType()
                payload["eventKind"] = "state_snapshot"
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = mapOf(
                    "activeSlot" to event.state.activeSlot.name,
                    "presetIds" to event.state.presetIds,
                    "presetNames" to event.state.slots.map { it.name },
                    "firmwareA4" to event.state.a4Reference,
                    "tempo" to event.state.tempo
                )
                payload["notes"] = null
            }
            is PedalRuntimeEvent.PresetDetailReceived -> {
                payload["source"] = "runtime"
                payload["messageType"] = event.messageType.toHexType()
                payload["eventKind"] = "preset_detail"
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = mapOf(
                    "presetName" to event.name,
                    "parameterCount" to event.parameters.size
                )
                payload["notes"] = null
            }
            is PedalRuntimeEvent.FrameReceived -> {
                payload["source"] = "runtime"
                payload["messageType"] = event.messageType.toHexType()
                payload["eventKind"] = "frame"
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = emptyMap<String, Any?>()
                payload["notes"] = null
            }
            is PedalRuntimeEvent.ParameterChanged -> {
                payload["source"] = "runtime"
                payload["messageType"] = event.messageType.toHexType()
                payload["eventKind"] = "parameter_change"
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = mapOf("paramIndex" to event.paramIndex, "value" to event.value)
                payload["notes"] = null
            }
            is PedalRuntimeEvent.TransportError -> {
                payload["source"] = "runtime"
                payload["messageType"] = null
                payload["eventKind"] = "transport_error"
                payload["payloadHex"] = event.payloadHex
                payload["parsed"] = mapOf("errorMessage" to event.errorMessage)
                payload["notes"] = null
            }
            PedalRuntimeEvent.Disconnected -> {
                payload["source"] = "runtime"
                payload["messageType"] = null
                payload["eventKind"] = "disconnect"
                payload["payloadHex"] = null
                payload["parsed"] = emptyMap<String, Any?>()
                payload["notes"] = null
            }
        }

        return encodeJson(payload)
    }

    private fun encodeJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.escapeJson()}\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "\"${key.toString().escapeJson()}\":${encodeJson(item)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { encodeJson(it) }
        else -> "\"${value.toString().escapeJson()}\""
    }

    private fun String.escapeJson(): String = buildString(length + 8) {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun Int.toHexType(): String = "0x${toString(16).padStart(4, '0')}"
}
