package com.opentonex.controller.ui.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Metronomo local: gera o PCM de cada tempo (clique + silencio) e escreve num AudioTrack em
 * streaming, entao o intervalo e' contado em amostras do relogio de audio, sem jitter de
 * timer. BPM, compasso, tom, volume e acento valem a partir do proximo tempo.
 */
class MetronomeEngine {
    @Volatile var bpm: Int = 120
    @Volatile var beatsPerBar: Int = 4
    @Volatile var toneHz: Double = 1000.0
    @Volatile var volume: Float = 0.8f
    @Volatile var accentEnabled: Boolean = true

    /** Uma execucao: o flag proprio evita que uma thread antiga reviva num start() seguinte. */
    private class Run { @Volatile var active = true }

    private var current: Run? = null

    /** [onBeat] roda na thread de audio com o indice do tempo (0 = primeiro do compasso). */
    @Synchronized
    fun start(onBeat: (Int) -> Unit) {
        if (current != null) return
        val run = Run().also { current = it }
        Thread({ play(run, onBeat) }, "tusb-metronome").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        current?.active = false
        current = null
    }

    private fun play(run: Run, onBeat: (Int) -> Unit) {
        val rate = MetronomeClick.SAMPLE_RATE
        val minBytes = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes, CHUNK_FRAMES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        try {
            track.play()
            var beat = 0
            while (run.active) {
                val frames = MetronomeClick.beatFrames(bpm, rate)
                val accent = accentEnabled && beat == 0
                val click = MetronomeClick.render(toneHz, accent, volume, rate)
                val samples = ShortArray(frames)
                click.copyInto(samples, 0, 0, minOf(click.size, frames))
                onBeat(beat)
                // Escreve em blocos curtos para o stop() ser atendido sem esperar o tempo todo.
                var offset = 0
                while (offset < frames && run.active) {
                    val written = track.write(samples, offset, minOf(CHUNK_FRAMES, frames - offset))
                    if (written < 0) return
                    offset += written
                }
                beat = (beat + 1) % beatsPerBar.coerceAtLeast(1)
            }
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
        }
    }

    private companion object {
        const val CHUNK_FRAMES = 2048
    }
}
