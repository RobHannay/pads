package com.worshippads.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes the magnitude response of the EQ chain in decibels at a given
 * frequency, for drawing the curve on the parametric-EQ screen.
 *
 * The audio processor has its own biquad state and coefficients; this helper
 * re-derives coefficients from the RBJ cookbook formulas the processor uses,
 * then evaluates |H(e^jω)| algebraically. No DSP state is shared — this is
 * pure math for visualisation.
 */
object EqResponse {

    private const val FS = 48_000.0

    /**
     * Map displayed cutoff to actual filter cutoff. Below 40 Hz the filter
     * is moved progressively further off the visible axis (down to 5 Hz at
     * UI = 20 Hz) so dragging the HPF dot near the left edge produces no
     * visible curve dip — the cut "fades in" smoothly as you drag right.
     * At 40 Hz and above the mapping is 1:1, so presets and any meaningful
     * HPF setting are unaffected.
     */
    fun effectiveHpfCutoff(uiCutoffHz: Int): Float = when {
        uiCutoffHz <= 20 -> 5f
        uiCutoffHz >= 40 -> uiCutoffHz.toFloat()
        else -> {
            val t = (uiCutoffHz - 20).toFloat() / 20f
            5f + t * 35f
        }
    }

    /**
     * Total EQ gain in dB at [freqHz] for the given config. Summing the dB
     * contributions of all enabled biquads is equivalent to multiplying their
     * linear magnitudes (they run in series).
     */
    fun responseDb(
        bassDb: Float,
        presenceDb: Float,
        trebleDb: Float,
        lowCutHz: Int,
        freqHz: Float,
    ): Float {
        var db = 0.0
        if (bassDb != 0f) {
            db += biquadMagnitudeDb(peakingCoefs(150.0, bassDb.toDouble(), 1.0), freqHz.toDouble())
        }
        if (presenceDb != 0f) {
            db += biquadMagnitudeDb(peakingCoefs(2500.0, presenceDb.toDouble(), 0.9), freqHz.toDouble())
        }
        if (trebleDb != 0f) {
            db += biquadMagnitudeDb(peakingCoefs(6000.0, trebleDb.toDouble(), 1.0), freqHz.toDouble())
        }
        if (lowCutHz > 0) {
            // Two cascaded identical high-pass biquads → 24 dB/oct slope.
            val cutoff = effectiveHpfCutoff(lowCutHz).toDouble()
            val coefs = highPassCoefs(cutoff, 0.707)
            db += 2.0 * biquadMagnitudeDb(coefs, freqHz.toDouble())
        }
        return db.toFloat()
    }

    /** Biquad coefficients, normalised by a0. Order: b0, b1, b2, a1, a2. */
    private data class Coefs(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    private fun biquadMagnitudeDb(c: Coefs, freqHz: Double): Double {
        val w = 2.0 * PI * freqHz / FS
        val cw = cos(w); val sw = sin(w)
        val c2w = cos(2 * w); val s2w = sin(2 * w)
        val numRe = c.b0 + c.b1 * cw + c.b2 * c2w
        val numIm = -(c.b1 * sw + c.b2 * s2w)
        val denRe = 1.0 + c.a1 * cw + c.a2 * c2w
        val denIm = -(c.a1 * sw + c.a2 * s2w)
        val numMagSq = numRe * numRe + numIm * numIm
        val denMagSq = denRe * denRe + denIm * denIm
        if (denMagSq <= 0.0 || numMagSq <= 0.0) return 0.0
        return 10.0 * log10(numMagSq / denMagSq)
    }

    private fun lowShelfCoefs(f: Double, gainDb: Double, q: Double): Coefs {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2.0 * PI * f / FS
        val cw = cos(w); val sw = sin(w)
        val alpha = sw / (2.0 * q)
        val twoSqA = 2.0 * sqrt(a)
        val a0 = (a + 1) + (a - 1) * cw + twoSqA * alpha
        return Coefs(
            b0 = a * ((a + 1) - (a - 1) * cw + twoSqA * alpha) / a0,
            b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0,
            b2 = a * ((a + 1) - (a - 1) * cw - twoSqA * alpha) / a0,
            a1 = -2 * ((a - 1) + (a + 1) * cw) / a0,
            a2 = ((a + 1) + (a - 1) * cw - twoSqA * alpha) / a0,
        )
    }

    private fun highShelfCoefs(f: Double, gainDb: Double, q: Double): Coefs {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2.0 * PI * f / FS
        val cw = cos(w); val sw = sin(w)
        val alpha = sw / (2.0 * q)
        val twoSqA = 2.0 * sqrt(a)
        val a0 = (a + 1) - (a - 1) * cw + twoSqA * alpha
        return Coefs(
            b0 = a * ((a + 1) + (a - 1) * cw + twoSqA * alpha) / a0,
            b1 = -2 * a * ((a - 1) + (a + 1) * cw) / a0,
            b2 = a * ((a + 1) + (a - 1) * cw - twoSqA * alpha) / a0,
            a1 = 2 * ((a - 1) - (a + 1) * cw) / a0,
            a2 = ((a + 1) - (a - 1) * cw - twoSqA * alpha) / a0,
        )
    }

    private fun peakingCoefs(f: Double, gainDb: Double, q: Double): Coefs {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2.0 * PI * f / FS
        val cw = cos(w); val sw = sin(w)
        val alpha = sw / (2.0 * q)
        val a0 = 1 + alpha / a
        return Coefs(
            b0 = (1 + alpha * a) / a0,
            b1 = (-2 * cw) / a0,
            b2 = (1 - alpha * a) / a0,
            a1 = (-2 * cw) / a0,
            a2 = (1 - alpha / a) / a0,
        )
    }

    private fun highPassCoefs(f: Double, q: Double): Coefs {
        val w = 2.0 * PI * f / FS
        val cw = cos(w); val sw = sin(w)
        val alpha = sw / (2.0 * q)
        val a0 = 1 + alpha
        return Coefs(
            b0 = ((1 + cw) / 2) / a0,
            b1 = (-(1 + cw)) / a0,
            b2 = ((1 + cw) / 2) / a0,
            a1 = (-2 * cw) / a0,
            a2 = (1 - alpha) / a0,
        )
    }
}
