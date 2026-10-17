package com.opentonex.controller.ui.tools

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeModelsTest {
    @Test
    fun tapTempoNeedsTwoTapsBeforeAnswering() {
        val tap = TapTempo()
        assertNull(tap.tap(0))
        assertEquals(120, tap.tap(500))
    }

    @Test
    fun tapTempoAveragesTheIntervals() {
        val tap = TapTempo()
        listOf(0L, 400L).forEach { tap.tap(it) }
        // Intervalos 400 e 600 -> media 500 ms -> 120 BPM.
        assertEquals(120, tap.tap(1000))
    }

    @Test
    fun tapTempoDropsTapsOlderThanTheWindow() {
        val tap = TapTempo(windowMs = 2500L)
        tap.tap(0)
        // O primeiro toque saiu da janela; este vira o unico e nao ha estimativa.
        assertNull(tap.tap(3000))
        assertEquals(120, tap.tap(3500))
    }

    @Test
    fun tapTempoClampsToTheMetronomeRange() {
        assertEquals(MAX_BPM, TapTempo().apply { tap(0) }.tap(100))
        assertEquals(MIN_BPM, TapTempo().apply { tap(0) }.tap(2400))
    }

    @Test
    fun beatFramesFollowTheBpmAndClamp() {
        assertEquals(22_050, MetronomeClick.beatFrames(120))
        assertEquals(66_150, MetronomeClick.beatFrames(10))
        assertEquals(MetronomeClick.beatFrames(MAX_BPM), MetronomeClick.beatFrames(1000))
    }

    @Test
    fun clickHasTheExpectedLengthAndStartsAtZero() {
        val click = MetronomeClick.render(1000.0, accent = false, volume = 1f)
        assertEquals(44_100 * 35 / 1000, click.size)
        assertEquals(0, click[0].toInt())
    }

    @Test
    fun clickNeverClipsAndZeroVolumeIsSilent() {
        val loud = MetronomeClick.render(1000.0, accent = true, volume = 2f)
        assertTrue(loud.maxOf { abs(it.toInt()) } <= Short.MAX_VALUE * 0.9)
        assertTrue(MetronomeClick.render(1000.0, accent = true, volume = 0f).all { it.toInt() == 0 })
    }

    @Test
    fun accentIsLouderThanARegularBeat() {
        val regular = MetronomeClick.render(1000.0, accent = false, volume = 1f)
        val accent = MetronomeClick.render(1000.0, accent = true, volume = 1f)
        assertTrue(accent.maxOf { abs(it.toInt()) } > regular.maxOf { abs(it.toInt()) })
    }

    @Test
    fun clickDecaysTowardTheEnd() {
        val click = MetronomeClick.render(1000.0, accent = false, volume = 1f)
        val head = click.take(200).maxOf { abs(it.toInt()) }
        val tail = click.takeLast(200).maxOf { abs(it.toInt()) }
        assertTrue(tail < head / 4)
    }

    @Test
    fun wheelAngleIsMeasuredFromTheCenter() {
        assertEquals(0f, BpmWheelMath.angleDegrees(10f, 0f, 0f, 0f), 0.001f)
        assertEquals(90f, BpmWheelMath.angleDegrees(0f, 10f, 0f, 0f), 0.001f)
        assertEquals(-90f, BpmWheelMath.angleDegrees(0f, -10f, 0f, 0f), 0.001f)
    }

    @Test
    fun wheelDeltaTakesTheShortestPathAcrossTheSeam() {
        assertEquals(15f, BpmWheelMath.deltaBpm(0f, 90f), 0.001f)
        // 170 -> -170 e' um giro de +20 graus (nao -340).
        assertEquals(20f / 360f * 60f, BpmWheelMath.deltaBpm(170f, -170f), 0.001f)
        assertEquals(-20f / 360f * 60f, BpmWheelMath.deltaBpm(-170f, 170f), 0.001f)
    }

    @Test
    fun aFullTurnOfTheWheelIsSixtyBpm() {
        var angle = 0f
        var total = 0f
        repeat(36) {
            val next = angle + 10f
            total += BpmWheelMath.deltaBpm(angle, if (next > 180f) next - 360f else next)
            angle = if (next > 180f) next - 360f else next
        }
        assertEquals(60f, total, 0.01f)
    }

    @Test
    fun timeSignaturesExposeTheirLabelAndBeatCount() {
        assertEquals("4/4", TimeSignature.FOUR_FOUR.label)
        assertEquals("6/8", TimeSignature.SIX_EIGHT.label)
        assertEquals(7, TimeSignature.SEVEN_EIGHT.beats)
        assertTrue(TimeSignature.entries.all { it.beats >= 2 })
    }
}
