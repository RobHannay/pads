package com.worshippads.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Media3 AudioProcessor that applies up to four bands of biquad EQ —
 * low shelf (bass), peaking (presence), high shelf (treble), and a
 * 24 dB/oct high-pass (low cut). Coefficients come from the shared
 * [EqConfig]; when all bands are at zero the processor bypasses and
 * copies input straight to output.
 */
class EqAudioProcessor(private val config: EqConfig) : BaseAudioProcessor() {

    private var sampleRate = 48000
    private var channelCount = 2

    private val bass = Biquad()
    private val presence = Biquad()
    private val treble = Biquad()
    private val hp1 = Biquad()
    private val hp2 = Biquad()

    private var activeStages: Array<Biquad> = emptyArray()

    // Cache the last applied config so we only recompute coefficients when
    // the user actually moved a slider.
    private var lastBass = Float.NaN
    private var lastPresence = Float.NaN
    private var lastTreble = Float.NaN
    private var lastLowCut = -1

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        for (b in arrayOf(bass, presence, treble, hp1, hp2)) b.resize(channelCount)
        lastBass = Float.NaN  // force recompute
        recomputeIfNeeded()
        return inputAudioFormat
    }

    private fun recomputeIfNeeded() {
        val b = config.bassDb
        val p = config.presenceDb
        val t = config.trebleDb
        val lc = config.lowCutHz
        if (b == lastBass && p == lastPresence && t == lastTreble && lc == lastLowCut) return
        lastBass = b; lastPresence = p; lastTreble = t; lastLowCut = lc

        val stages = mutableListOf<Biquad>()
        if (b != 0f) { bass.setLowShelf(sampleRate, 150f, b, 0.707f); stages += bass }
        if (p != 0f) { presence.setPeaking(sampleRate, 2500f, p, 0.9f); stages += presence }
        if (t != 0f) { treble.setHighShelf(sampleRate, 6000f, t, 0.707f); stages += treble }
        if (lc > 0) {
            hp1.setHighPass(sampleRate, lc.toFloat(), 0.707f)
            hp2.setHighPass(sampleRate, lc.toFloat(), 0.707f)
            stages += hp1
            stages += hp2
        }
        activeStages = stages.toTypedArray()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        recomputeIfNeeded()

        val shortIn = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = shortIn.remaining() / channelCount
        if (frames == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val bytes = frames * channelCount * 2
        val out = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        val stages = activeStages

        if (stages.isEmpty()) {
            // Bypass — copy PCM16 straight through.
            out.asShortBuffer().put(shortIn)
            out.position(bytes)
        } else {
            for (f in 0 until frames) {
                for (ch in 0 until channelCount) {
                    var sample = shortIn.get().toInt() / 32768f
                    for (s in stages.indices) {
                        sample = stages[s].process(ch, sample)
                    }
                    if (sample > 1f) sample = 1f
                    if (sample < -1f) sample = -1f
                    out.putShort((sample * 32767f).toInt().toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush() {
        bass.reset(); presence.reset(); treble.reset(); hp1.reset(); hp2.reset()
    }

    override fun onReset() {
        bass.reset(); presence.reset(); treble.reset(); hp1.reset(); hp2.reset()
    }
}

/**
 * Single biquad section using Direct-Form-II-Transposed with per-channel
 * state. Coefficient formulas come from Robert Bristow-Johnson's Audio EQ
 * Cookbook (https://www.w3.org/TR/audio-eq-cookbook/).
 */
private class Biquad {
    var b0 = 1f; var b1 = 0f; var b2 = 0f
    var a1 = 0f; var a2 = 0f

    private var z1: FloatArray = FloatArray(0)
    private var z2: FloatArray = FloatArray(0)

    fun resize(channels: Int) {
        z1 = FloatArray(channels)
        z2 = FloatArray(channels)
    }

    fun reset() {
        z1.fill(0f)
        z2.fill(0f)
    }

    fun process(ch: Int, x: Float): Float {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x + z2[ch] - a1 * y
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun setLowShelf(sr: Int, fHz: Float, gainDb: Float, q: Float) {
        val A = 10f.pow(gainDb / 40f)
        val w = 2.0 * PI * fHz / sr
        val cosw = cos(w).toFloat()
        val sinw = sin(w).toFloat()
        val alpha = sinw / (2f * q)
        val twoSqA = 2f * sqrt(A)

        val a0 = (A + 1f) + (A - 1f) * cosw + twoSqA * alpha
        val nb0 = A * ((A + 1f) - (A - 1f) * cosw + twoSqA * alpha)
        val nb1 = 2f * A * ((A - 1f) - (A + 1f) * cosw)
        val nb2 = A * ((A + 1f) - (A - 1f) * cosw - twoSqA * alpha)
        val na1 = -2f * ((A - 1f) + (A + 1f) * cosw)
        val na2 = (A + 1f) + (A - 1f) * cosw - twoSqA * alpha

        normalize(a0, nb0, nb1, nb2, na1, na2)
    }

    fun setHighShelf(sr: Int, fHz: Float, gainDb: Float, q: Float) {
        val A = 10f.pow(gainDb / 40f)
        val w = 2.0 * PI * fHz / sr
        val cosw = cos(w).toFloat()
        val sinw = sin(w).toFloat()
        val alpha = sinw / (2f * q)
        val twoSqA = 2f * sqrt(A)

        val a0 = (A + 1f) - (A - 1f) * cosw + twoSqA * alpha
        val nb0 = A * ((A + 1f) + (A - 1f) * cosw + twoSqA * alpha)
        val nb1 = -2f * A * ((A - 1f) + (A + 1f) * cosw)
        val nb2 = A * ((A + 1f) + (A - 1f) * cosw - twoSqA * alpha)
        val na1 = 2f * ((A - 1f) - (A + 1f) * cosw)
        val na2 = (A + 1f) - (A - 1f) * cosw - twoSqA * alpha

        normalize(a0, nb0, nb1, nb2, na1, na2)
    }

    fun setPeaking(sr: Int, fHz: Float, gainDb: Float, q: Float) {
        val A = 10f.pow(gainDb / 40f)
        val w = 2.0 * PI * fHz / sr
        val cosw = cos(w).toFloat()
        val sinw = sin(w).toFloat()
        val alpha = sinw / (2f * q)

        val a0 = 1f + alpha / A
        val nb0 = 1f + alpha * A
        val nb1 = -2f * cosw
        val nb2 = 1f - alpha * A
        val na1 = -2f * cosw
        val na2 = 1f - alpha / A

        normalize(a0, nb0, nb1, nb2, na1, na2)
    }

    fun setHighPass(sr: Int, fHz: Float, q: Float) {
        val w = 2.0 * PI * fHz / sr
        val cosw = cos(w).toFloat()
        val sinw = sin(w).toFloat()
        val alpha = sinw / (2f * q)

        val a0 = 1f + alpha
        val nb0 = (1f + cosw) / 2f
        val nb1 = -(1f + cosw)
        val nb2 = (1f + cosw) / 2f
        val na1 = -2f * cosw
        val na2 = 1f - alpha

        normalize(a0, nb0, nb1, nb2, na1, na2)
    }

    private fun normalize(a0: Float, nb0: Float, nb1: Float, nb2: Float, na1: Float, na2: Float) {
        b0 = nb0 / a0
        b1 = nb1 / a0
        b2 = nb2 / a0
        a1 = na1 / a0
        a2 = na2 / a0
    }
}
