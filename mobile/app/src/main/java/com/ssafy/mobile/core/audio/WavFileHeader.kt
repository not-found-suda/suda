package com.ssafy.mobile.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WavFileHeader {
    const val HEADER_SIZE_BYTES = 44

    fun create(
        pcmDataSize: Long,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val byteRate = sampleRate * channelCount * bitsPerSample / BITS_PER_BYTE
        val blockAlign = channelCount * bitsPerSample / BITS_PER_BYTE
        val riffChunkSize = pcmDataSize + RIFF_CHUNK_SIZE_OFFSET

        return ByteBuffer
            .allocate(HEADER_SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putAscii("RIFF")
            .putInt(riffChunkSize.toInt())
            .putAscii("WAVE")
            .putAscii("fmt ")
            .putInt(PCM_FORMAT_CHUNK_SIZE)
            .putShort(PCM_AUDIO_FORMAT)
            .putShort(channelCount.toShort())
            .putInt(sampleRate)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(bitsPerSample.toShort())
            .putAscii("data")
            .putInt(pcmDataSize.toInt())
            .array()
    }

    private fun ByteBuffer.putAscii(value: String): ByteBuffer {
        value.forEach { put(it.code.toByte()) }
        return this
    }

    private const val BITS_PER_BYTE = 8
    private const val RIFF_CHUNK_SIZE_OFFSET = 36
    private const val PCM_FORMAT_CHUNK_SIZE = 16
    private const val PCM_AUDIO_FORMAT = 1.toShort()
}
