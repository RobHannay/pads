package com.worshippads.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.breakfastquay.rubberband.RubberBandLiveShifter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Media3 AudioProcessor that runs PCM16 through rubberband's LiveShifter for
 * real-time pitch shifting. Inserted into ExoPlayer's audio processor chain.
 *
 * Pitch is controlled by [octave] (integer −1 / 0 / +1); changes take effect
 * on the next block. Setting 0 is still routed through the shifter (for a
 * few ms of extra latency) — simpler than bypassing the processor conditionally.
 */
class RubberbandAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var octave: Int = 0
        set(value) {
            field = value
            shifter?.setPitchScale(2.0.pow(value))
        }

    private var shifter: RubberBandLiveShifter? = null
    private var blockSize = 0
    private var channelCount = 0

    // Per-channel planar buffers: input accumulates until a full block, then
    // rubberband shifts it into the output accumulator. Both grow on demand.
    private var inputPlanes: Array<FloatArray> = emptyArray()
    private var inputFill = 0
    private var outputPlanes: Array<FloatArray> = emptyArray()
    private var outputFill = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        disposeShifter()
        val s = RubberBandLiveShifter(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            RubberBandLiveShifter.OptionWindowMedium or
                RubberBandLiveShifter.OptionChannelsTogether
        )
        s.setPitchScale(2.0.pow(octave))
        shifter = s
        blockSize = s.blockSize
        channelCount = inputAudioFormat.channelCount

        // Size the accumulators for one block worth. Output can grow above
        // blockSize before the consumer drains it; we resize if we overrun.
        inputPlanes = Array(channelCount) { FloatArray(blockSize) }
        outputPlanes = Array(channelCount) { FloatArray(blockSize * 4) }
        inputFill = 0
        outputFill = 0

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val shortBuf = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val framesAvailable = shortBuf.remaining() / channelCount
        if (framesAvailable == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        var consumed = 0
        while (consumed < framesAvailable) {
            val need = blockSize - inputFill
            val take = minOf(need, framesAvailable - consumed)

            // Deinterleave into per-channel input planes.
            for (f in 0 until take) {
                for (ch in 0 until channelCount) {
                    val s = shortBuf.get()
                    inputPlanes[ch][inputFill + f] = s / 32768f
                }
            }
            inputFill += take
            consumed += take

            if (inputFill == blockSize) {
                ensureOutputCapacity(outputFill + blockSize)
                val outSlice = Array(channelCount) { ch ->
                    FloatArray(blockSize)
                }
                shifter!!.shift(inputPlanes, 0, outSlice, 0)
                for (ch in 0 until channelCount) {
                    System.arraycopy(outSlice[ch], 0, outputPlanes[ch], outputFill, blockSize)
                }
                outputFill += blockSize
                inputFill = 0
            }
        }

        inputBuffer.position(inputBuffer.limit())

        if (outputFill > 0) {
            emitOutput()
        }
    }

    private fun ensureOutputCapacity(frames: Int) {
        if (outputPlanes.isEmpty()) return
        val cap = outputPlanes[0].size
        if (frames <= cap) return
        var newCap = cap
        while (newCap < frames) newCap *= 2
        for (ch in 0 until channelCount) {
            outputPlanes[ch] = outputPlanes[ch].copyOf(newCap)
        }
    }

    private fun emitOutput() {
        val frames = outputFill
        val bytes = frames * channelCount * 2
        val out = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        for (f in 0 until frames) {
            for (ch in 0 until channelCount) {
                val v = outputPlanes[ch][f].coerceIn(-1f, 1f)
                out.putShort((v * 32767f).toInt().toShort())
            }
        }
        out.flip()
        outputFill = 0
    }

    override fun onQueueEndOfStream() {
        // Flush remaining output. We don't have a way to push residual rubberband
        // samples out without more input; our crossfade finishes pads cleanly
        // anyway so this should rarely carry audible residue.
        if (outputFill > 0) emitOutput()
    }

    override fun onFlush() {
        inputFill = 0
        outputFill = 0
        shifter?.reset()
    }

    override fun onReset() {
        disposeShifter()
    }

    private fun disposeShifter() {
        shifter?.dispose()
        shifter = null
    }
}
