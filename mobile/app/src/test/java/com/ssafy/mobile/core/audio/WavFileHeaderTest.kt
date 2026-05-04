package com.ssafy.mobile.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class WavFileHeaderTest {
    @Test
    fun `creates PCM mono WAV header`() {
        val header =
            WavFileHeader.create(
                pcmDataSize = PCM_DATA_SIZE,
                sampleRate = SAMPLE_RATE,
                channelCount = CHANNEL_COUNT,
                bitsPerSample = BITS_PER_SAMPLE,
            )
        val littleEndianHeader = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(WavFileHeader.HEADER_SIZE_BYTES, header.size)
        assertEquals("RIFF", header.asAscii(start = 0, length = 4))
        assertEquals(PCM_DATA_SIZE + RIFF_CHUNK_SIZE_OFFSET, littleEndianHeader.getInt(4).toLong())
        assertEquals("WAVE", header.asAscii(start = 8, length = 4))
        assertEquals("fmt ", header.asAscii(start = 12, length = 4))
        assertEquals(PCM_FORMAT_CHUNK_SIZE, littleEndianHeader.getInt(16))
        assertEquals(PCM_AUDIO_FORMAT, littleEndianHeader.getShort(20).toInt())
        assertEquals(CHANNEL_COUNT, littleEndianHeader.getShort(22).toInt())
        assertEquals(SAMPLE_RATE, littleEndianHeader.getInt(24))
        assertEquals(BYTE_RATE, littleEndianHeader.getInt(28))
        assertEquals(BLOCK_ALIGN, littleEndianHeader.getShort(32).toInt())
        assertEquals(BITS_PER_SAMPLE, littleEndianHeader.getShort(34).toInt())
        assertEquals("data", header.asAscii(start = 36, length = 4))
        assertEquals(PCM_DATA_SIZE, littleEndianHeader.getInt(40).toLong())
    }

    private fun ByteArray.asAscii(
        start: Int,
        length: Int,
    ): String = copyOfRange(start, start + length).toString(Charsets.US_ASCII)

    private companion object {
        private const val PCM_DATA_SIZE = 3200L
        private const val RIFF_CHUNK_SIZE_OFFSET = 36
        private const val PCM_FORMAT_CHUNK_SIZE = 16
        private const val PCM_AUDIO_FORMAT = 1
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTE_RATE = 32000
        private const val BLOCK_ALIGN = 2
    }
}
