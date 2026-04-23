package com.worshippads.audio

import android.util.Log
import com.breakfastquay.rubberband.RubberBandLiveShifter
import kotlin.system.measureNanoTime

/**
 * Smoke-test the rubberband JNI binding: instantiate a LiveShifter, feed it
 * a buffer of silence at half pitch, log what happens. Intended to run once
 * on startup so a broken native lib is caught immediately rather than later
 * when the first pitch change is requested.
 */
object RubberbandSmokeTest {
    private const val TAG = "RubberbandSmoke"

    fun run(): Result {
        return try {
            val sampleRate = 48000
            val channels = 2
            val shifter = RubberBandLiveShifter(
                sampleRate,
                channels,
                RubberBandLiveShifter.OptionWindowMedium or
                    RubberBandLiveShifter.OptionChannelsTogether
            )
            shifter.setPitchScale(0.5)

            val block = shifter.blockSize
            val input = Array(channels) { FloatArray(block) }
            val output = Array(channels) { FloatArray(block) }

            // One pass of silence through the engine; measures JNI round-trip cost.
            val nanos = measureNanoTime { shifter.shift(input, output) }

            val startDelay = shifter.startDelay
            shifter.dispose()

            Result.Ok(
                blockSize = block,
                startDelay = startDelay,
                shiftMicros = nanos / 1_000L
            ).also { Log.i(TAG, it.toString()) }
        } catch (e: Throwable) {
            Log.e(TAG, "rubberband JNI load/run failed", e)
            Result.Fail(e.javaClass.simpleName, e.message ?: "")
        }
    }

    sealed class Result {
        data class Ok(val blockSize: Int, val startDelay: Int, val shiftMicros: Long) : Result() {
            override fun toString() =
                "rubberband OK: blockSize=$blockSize startDelay=$startDelay shift=${shiftMicros}µs"
        }
        data class Fail(val type: String, val message: String) : Result() {
            override fun toString() = "rubberband FAIL: $type: $message"
        }
    }
}
