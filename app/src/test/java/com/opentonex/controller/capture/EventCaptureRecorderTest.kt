package com.opentonex.controller.capture

import com.opentonex.controller.connection.PedalRuntimeEvent
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCaptureRecorderTest {
    @Test fun `record writes one json line per runtime event`() {
        val root = createTempDir(prefix = "tonex-capture-test-")
        val recorder = EventCaptureRecorder(rootDirectory = root, nowMs = { 123L })
        val session = recorder.startSession()

        recorder.record(
            PedalRuntimeEvent.LocalAction(
                action = "start_capture",
                details = mapOf("from" to "test")
            )
        )
        recorder.record(
            PedalRuntimeEvent.PresetDetailReceived(
                name = "Preset Teste",
                messageType = 0x0304,
                payloadHex = "B9 03"
            )
        )
        recorder.stopSession()

        val lines = session.file.readLines()
        assertTrue(lines.size == 2)
        assertTrue(lines[0].contains("\"eventKind\":\"start_capture\""))
        assertTrue(lines[1].contains("\"messageType\":\"0x0304\""))
        assertTrue(lines[1].contains("\"presetName\":\"Preset Teste\""))

        root.deleteRecursively()
    }
}
