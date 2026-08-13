package com.danielsalas.auto_music.player.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

enum class FilterType { PK, LSC, HSC }

class BiquadFilter(
    private val sampleRate: Int,
    private val frequency: Double,
    private val gain: Double,
    private val q: Double = 1.41,
    private val filterType: FilterType = FilterType.PK
) {
    private var a0 = 1.0; private var a1 = 0.0; private var a2 = 0.0
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private var x1L = 0.0; private var x2L = 0.0; private var y1L = 0.0; private var y2L = 0.0
    private var x1R = 0.0; private var x2R = 0.0; private var y1R = 0.0; private var y2R = 0.0

    init { calculateCoefficients() }

    private fun calculateCoefficients() {
        val A = 10.0.pow(gain / 40.0)
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega); val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        when (filterType) {
            FilterType.PK -> {
                b0 = 1.0 + alpha * A; b1 = -2.0 * cosOmega; b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A; a1 = -2.0 * cosOmega; a2 = 1.0 - alpha / A
            }
            else -> {
                b0 = 1.0 + alpha * A; b1 = -2.0 * cosOmega; b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A; a1 = -2.0 * cosOmega; a2 = 1.0 - alpha / A
            }
        }
        b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0; a0 = 1.0
    }

    fun processSample(input: Double): Double {
        val out = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = input; y2L = y1L; y1L = out
        return out
    }

    fun processStereo(inL: Double, inR: Double): Pair<Double, Double> {
        val outL = b0 * inL + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = inL; y2L = y1L; y1L = outL
        val outR = b0 * inR + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R; x1R = inR; y2R = y1R; y1R = outR
        return Pair(outL, outR)
    }

    fun reset() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }
}
