package com.opentonex.controller.ui.tools

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin

const val MIN_BPM = 40
const val MAX_BPM = 240

/** Compassos oferecidos no metronomo. O BPM conta os tempos da unidade do compasso. */
enum class TimeSignature(val beats: Int, val unit: Int) {
    TWO_FOUR(2, 4),
    THREE_FOUR(3, 4),
    FOUR_FOUR(4, 4),
    FIVE_FOUR(5, 4),
    SIX_EIGHT(6, 8),
    SEVEN_EIGHT(7, 8);

    val label: String get() = "$beats/$unit"
}

/** Calcula o BPM a partir de toques consecutivos, descartando toques fora da janela. */
class TapTempo(private val windowMs: Long = 2500L) {
    private val taps = ArrayDeque<Long>()

    /** Registra um toque; devolve o BPM estimado ou null com menos de dois toques na janela. */
    fun tap(nowMs: Long): Int? {
        taps.removeAll { nowMs - it > windowMs }
        taps.addLast(nowMs)
        if (taps.size < 2) return null
        val averageMs = taps.zipWithNext { a, b -> b - a }.average()
        if (averageMs <= 0.0) return null
        return (60_000.0 / averageMs).toInt().coerceIn(MIN_BPM, MAX_BPM)
    }
}

/** Sintese do clique do metronomo: seno curto com decaimento exponencial. */
object MetronomeClick {
    const val SAMPLE_RATE = 44_100
    const val MIN_TONE_HZ = 400f
    const val MAX_TONE_HZ = 2400f
    private const val CLICK_MS = 35
    private const val DECAY_PER_SECOND = 90.0
    private const val ACCENT_PITCH_RATIO = 1.5
    private const val NORMAL_GAIN = 0.75
    private const val HEADROOM = 0.9

    /** Amostras de um clique; o acento soa mais agudo e mais forte que o tempo comum. */
    fun render(frequencyHz: Double, accent: Boolean, volume: Float, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val length = sampleRate * CLICK_MS / 1000
        val pitch = if (accent) frequencyHz * ACCENT_PITCH_RATIO else frequencyHz
        val gain = volume.coerceIn(0f, 1f) * (if (accent) 1.0 else NORMAL_GAIN) * HEADROOM * Short.MAX_VALUE
        return ShortArray(length) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2 * PI * pitch * t) * exp(-t * DECAY_PER_SECOND) * gain).toInt().toShort()
        }
    }

    /** Quadros de audio entre dois tempos consecutivos. */
    fun beatFrames(bpm: Int, sampleRate: Int = SAMPLE_RATE): Int =
        (sampleRate * 60L / bpm.coerceIn(MIN_BPM, MAX_BPM)).toInt()
}

/** Geometria da roda de BPM: uma volta completa vale [BPM_PER_TURN] batidas por minuto. */
object BpmWheelMath {
    const val BPM_PER_TURN = 60f

    fun angleDegrees(x: Float, y: Float, centerX: Float, centerY: Float): Float =
        Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()

    /** Variacao de BPM entre dois angulos, pelo caminho mais curto (sem salto em +-180). */
    fun deltaBpm(fromDegrees: Float, toDegrees: Float): Float {
        var delta = toDegrees - fromDegrees
        while (delta > 180f) delta -= 360f
        while (delta <= -180f) delta += 360f
        return delta / 360f * BPM_PER_TURN
    }
}
