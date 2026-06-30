package com.opentonex.controller.connection

import com.opentonex.controller.domain.PedalState

sealed interface PedalRuntimeEvent {
    data class LocalAction(
        val action: String,
        val details: Map<String, Any?> = emptyMap(),
        val notes: String? = null
    ) : PedalRuntimeEvent

    data class RequestSent(
        val requestKind: String,
        val messageType: Int?,
        val payloadHex: String
    ) : PedalRuntimeEvent

    data class HelloResponseReceived(
        val firmwareVersion: String,
        val messageType: Int,
        val payloadHex: String
    ) : PedalRuntimeEvent

    data class StateReceived(
        val state: PedalState,
        val messageType: Int,
        val payloadHex: String
    ) : PedalRuntimeEvent

    data class PresetDetailReceived(
        val name: String,
        val messageType: Int,
        val payloadHex: String
    ) : PedalRuntimeEvent

    data class TransportError(
        val errorMessage: String,
        val payloadHex: String? = null
    ) : PedalRuntimeEvent

    data object Disconnected : PedalRuntimeEvent
}
