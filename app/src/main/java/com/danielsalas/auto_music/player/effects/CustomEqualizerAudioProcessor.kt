package com.danielsalas.auto_music.player.effects

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {
    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var eqEnabled = false
    private var filters: List<BiquadFilter> = emptyList()
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    
    private var pendingLevels: IntArray? = null

    @Synchronized
    fun updateSettings(enabled: Boolean, levels: IntArray?) {
        eqEnabled = enabled
        if (sampleRate == 0) {
            pendingLevels = levels
            return
        }
        createFilters(levels)
    }

    private fun createFilters(levels: IntArray?) {
        if (sampleRate == 0 || levels == null) return
        val frequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        val newList = mutableListOf<BiquadFilter>()
        for (i in levels.indices) {
            if (i < frequencies.size) {
                newList.add(BiquadFilter(sampleRate, frequencies[i], levels[i].toDouble() / 100.0))
            }
        }
        filters = newList
    }

    override fun configure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = format.sampleRate
        channelCount = format.channelCount
        encoding = format.encoding
        
        pendingLevels?.let { createFilters(it); pendingLevels = null }

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }
        isActive = true
        return format
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!eqEnabled || filters.isEmpty()) {
            val rem = inputBuffer.remaining()
            if (rem == 0) return
            if (outputBuffer.capacity() < rem) {
                outputBuffer = ByteBuffer.allocateDirect(rem).order(ByteOrder.nativeOrder())
            } else outputBuffer.clear()
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else outputBuffer.clear()

        val sampleCount = inputSize / 2
        repeat(sampleCount / channelCount) {
            if (channelCount == 1) {
                val s = inputBuffer.getShort().toDouble() / 32768.0
                var p = s
                for (f in filters) p = f.processSample(p)
                outputBuffer.putShort((p * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            } else {
                val sL = inputBuffer.getShort().toDouble() / 32768.0
                val sR = inputBuffer.getShort().toDouble() / 32768.0
                var pL = sL; var pR = sR
                for (f in filters) {
                    val res = f.processStereo(pL, pR)
                    pL = res.first; pR = res.second
                }
                outputBuffer.putShort((pL * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                outputBuffer.putShort((pR * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val b = outputBuffer; outputBuffer = AudioProcessor.EMPTY_BUFFER; return b
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0
    override fun flush() { outputBuffer = AudioProcessor.EMPTY_BUFFER; inputEnded = false; filters.forEach { it.reset() } }
    override fun reset() { flush(); sampleRate = 0; channelCount = 0; isActive = false }
    override fun queueEndOfStream() { inputEnded = true }
}
