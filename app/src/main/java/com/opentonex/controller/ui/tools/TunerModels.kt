package com.opentonex.controller.ui.tools

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

data class TuningNote(
    val label: String,
    val frequencyHz: Double
)

enum class TuningPreset(
    val notes: List<TuningNote>
) {
    STANDARD(
        listOf(
            TuningNote("E2", 82.41),
            TuningNote("A2", 110.00),
            TuningNote("D3", 146.83),
            TuningNote("G3", 196.00),
            TuningNote("B3", 246.94),
            TuningNote("E4", 329.63)
        )
    ),
    DROP_D(
        listOf(
            TuningNote("D2", 73.42),
            TuningNote("A2", 110.00),
            TuningNote("D3", 146.83),
            TuningNote("G3", 196.00),
            TuningNote("B3", 246.94),
            TuningNote("E4", 329.63)
        )
    ),
    HALF_STEP_DOWN(
        listOf(
            TuningNote("Eb2", 77.78),
            TuningNote("Ab2", 103.83),
            TuningNote("Db3", 138.59),
            TuningNote("Gb3", 185.00),
            TuningNote("Bb3", 233.08),
            TuningNote("Eb4", 311.13)
        )
    ),
    D_STANDARD(
        listOf(
            TuningNote("D2", 73.42),
            TuningNote("G2", 98.00),
            TuningNote("C3", 130.81),
            TuningNote("F3", 174.61),
            TuningNote("A3", 220.00),
            TuningNote("D4", 293.66)
        )
    ),
    OPEN_G(
        listOf(
            TuningNote("D2", 73.42),
            TuningNote("G2", 98.00),
            TuningNote("D3", 146.83),
            TuningNote("G3", 196.00),
            TuningNote("B3", 246.94),
            TuningNote("D4", 293.66)
        )
    ),
    DADGAD(
        listOf(
            TuningNote("D2", 73.42),
            TuningNote("A2", 110.00),
            TuningNote("D3", 146.83),
            TuningNote("G3", 196.00),
            TuningNote("A3", 220.00),
            TuningNote("D4", 293.66)
        )
    )
}

data class TunerReading(
    val detectedFrequencyHz: Double,
    val target: TuningNote,
    val cents: Double
) {
    val isInTune: Boolean = abs(cents) <= IN_TUNE_CENTS
}

fun nearestTarget(frequencyHz: Double, notes: List<TuningNote>): TuningNote =
    notes.minBy { abs(centsOff(frequencyHz, it.frequencyHz)) }

fun centsOff(frequencyHz: Double, targetFrequencyHz: Double): Double =
    1200.0 * (ln(frequencyHz / targetFrequencyHz) / ln(2.0))

fun readingFor(
    frequencyHz: Double,
    preset: TuningPreset,
    selectedStringIndex: Int?
): TunerReading {
    val target = selectedStringIndex
        ?.let { preset.notes.getOrNull(it) }
        ?: nearestTarget(frequencyHz, preset.notes)
    return TunerReading(
        detectedFrequencyHz = frequencyHz,
        target = target,
        cents = centsOff(frequencyHz, target.frequencyHz)
    )
}

fun detectPitchHz(
    samples: ShortArray,
    sampleRate: Int,
    minFrequencyHz: Double = 60.0,
    maxFrequencyHz: Double = 1200.0
): Double? {
    if (samples.isEmpty()) return null

    val normalized = FloatArray(samples.size)
    var sumSquares = 0.0
    for (index in samples.indices) {
        val sample = samples[index] / Short.MAX_VALUE.toFloat()
        normalized[index] = sample
        sumSquares += sample * sample
    }
    val rms = sqrt(sumSquares / samples.size)
    if (rms < MIN_RMS) return null

    val minLag = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(1)
    val maxLag = (sampleRate / minFrequencyHz).toInt().coerceAtMost(samples.size - 1)
    var bestLag = 0
    var bestCorrelation = 0.0
    val correlations = DoubleArray(maxLag + 1)

    for (lag in minLag..maxLag) {
        var correlation = 0.0
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        val limit = samples.size - lag
        for (index in 0 until limit) {
            val left = normalized[index]
            val right = normalized[index + lag]
            correlation += left * right
            leftEnergy += left * left
            rightEnergy += right * right
        }
        val normalizedCorrelation = correlation / sqrt(leftEnergy * rightEnergy + 1e-12)
        correlations[lag] = normalizedCorrelation
        if (normalizedCorrelation > bestCorrelation) {
            bestCorrelation = normalizedCorrelation
            bestLag = lag
        }
    }

    if (bestLag == 0 || bestCorrelation < MIN_CORRELATION) return null
    val selectedLag = ((minLag + 1) until maxLag).firstOrNull { lag ->
        correlations[lag] >= bestCorrelation * EARLY_PEAK_TOLERANCE &&
            correlations[lag] >= correlations[lag - 1] &&
            correlations[lag] >= correlations[lag + 1]
    } ?: bestLag
    return sampleRate.toDouble() / selectedLag
}

fun generateSineSamples(
    frequencyHz: Double,
    sampleRate: Int,
    size: Int,
    amplitude: Double = 0.8
): ShortArray =
    ShortArray(size) { index ->
        val value = kotlin.math.sin(2.0 * Math.PI * frequencyHz * index / sampleRate)
        (value * amplitude * Short.MAX_VALUE).toInt().toShort()
    }

private const val IN_TUNE_CENTS = 5.0
private const val MIN_RMS = 0.01
private const val MIN_CORRELATION = 0.45
private const val EARLY_PEAK_TOLERANCE = 0.92
