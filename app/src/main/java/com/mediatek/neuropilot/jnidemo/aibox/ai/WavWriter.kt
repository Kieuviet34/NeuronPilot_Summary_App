package com.mediatek.neuropilot.jnidemo.aibox.ai

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun toWavBytes(pcm16: ShortArray, sampleRate: Int = 16000, channels: Int = 1): ByteArray {
        val byteRate = sampleRate * channels * 2
        val dataSize = pcm16.size * 2
        val totalSize = 44 + dataSize
        val out = ByteArrayOutputStream(totalSize)

        fun writeAscii(value: String) {
            out.write(value.toByteArray(Charsets.US_ASCII))
        }

        fun writeInt(value: Int) {
            out.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            )
        }

        fun writeShort(value: Short) {
            out.write(
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
            )
        }

        writeAscii("RIFF")
        writeInt(36 + dataSize)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeInt(16)
        writeShort(1)
        writeShort(channels.toShort())
        writeInt(sampleRate)
        writeInt(byteRate)
        writeShort((channels * 2).toShort())
        writeShort(16)
        writeAscii("data")
        writeInt(dataSize)

        for (sample in pcm16) {
            writeShort(sample)
        }

        return out.toByteArray()
    }
}
