package com.ssafy.mobile.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Android AudioRecord를 사용하여 Clova STT 업로드용 PCM WAV 파일을 생성한다.
 */
@Singleton
@Suppress("TooManyFunctions")
class AndroidAudioRecorder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private var audioRecord: AudioRecord? = null
        private var outputFile: File? = null
        private var outputStream: FileOutputStream? = null
        private var recordingThread: Thread? = null
        private val maxAmplitude = AtomicInteger(0)

        @Volatile
        private var isRecording = false

        /**
         * 녹음 시작
         * @param fileName 저장할 파일 이름 (확장자 제외)
         */
        @Suppress("TooGenericExceptionCaught")
        fun start(fileName: String = "stt_audio"): Boolean {
            if (audioRecord != null) stop()

            return if (!hasRecordAudioPermission()) {
                Log.w(TAG, "Missing RECORD_AUDIO permission")
                false
            } else {
                val minBufferSize =
                    AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                    )

                if (minBufferSize <= 0) {
                    Log.w(TAG, "Invalid AudioRecord min buffer size: $minBufferSize")
                    false
                } else {
                    startWavRecording(fileName, minBufferSize)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun startWavRecording(
            fileName: String,
            minBufferSize: Int,
        ): Boolean {
            val file =
                File(context.cacheDir, "$fileName.wav").apply {
                    if (exists()) delete()
                }
            val bufferSize = maxOf(minBufferSize, MIN_RECORDING_BUFFER_BYTES)
            var recorder: AudioRecord? = null
            var stream: FileOutputStream? = null

            val isStarted =
                try {
                    val activeStream = FileOutputStream(file)
                    val activeRecorder = createAudioRecord(bufferSize)
                    stream = activeStream
                    recorder = activeRecorder
                    if (activeRecorder.state != AudioRecord.STATE_INITIALIZED) {
                        Log.w(TAG, "AudioRecord is not initialized")
                        false
                    } else {
                        activeStream.write(createWavHeader(pcmDataSize = 0))
                        activeRecorder.startRecording()

                        audioRecord = activeRecorder
                        outputFile = file
                        outputStream = activeStream
                        isRecording = true
                        maxAmplitude.set(0)
                        recordingThread =
                            Thread(
                                {
                                    writePcmAudio(activeRecorder, activeStream, bufferSize)
                                },
                                RECORDING_THREAD_NAME,
                            ).also { it.start() }
                        true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start WAV audio recording", e)
                    false
                }

            if (!isStarted) cleanupFailedStart(recorder, stream, file)
            return isStarted
        }

        @SuppressLint("MissingPermission")
        private fun createAudioRecord(bufferSize: Int): AudioRecord =
            AudioRecord
                .Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build(),
                ).setBufferSizeInBytes(bufferSize)
                .build()

        /**
         * 녹음 중지 및 결과 파일 반환
         */
        @Suppress("TooGenericExceptionCaught")
        fun stop(): File? {
            val activeRecorder = audioRecord ?: return null
            var isSuccess = true

            isRecording = false
            try {
                if (activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    activeRecorder.stop()
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to stop WAV audio recording", e)
                isSuccess = false
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to stop WAV audio recording", e)
                isSuccess = false
            }

            joinRecordingThread()

            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close WAV output stream", e)
                isSuccess = false
            } finally {
                activeRecorder.release()
            }

            return handleOutputFile(isSuccess)
        }

        fun getMaxAmplitude(): Int = maxAmplitude.getAndSet(0)

        private fun writePcmAudio(
            recorder: AudioRecord,
            stream: FileOutputStream,
            bufferSize: Int,
        ) {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    try {
                        stream.write(buffer, 0, bytesRead)
                        updateMaxAmplitude(buffer, bytesRead)
                    } catch (e: IOException) {
                        Log.w(TAG, "Failed to write WAV audio data", e)
                        isRecording = false
                    }
                } else if (bytesRead < 0) {
                    Log.w(TAG, "AudioRecord read failed: $bytesRead")
                    isRecording = false
                }
            }
        }

        private fun updateMaxAmplitude(
            buffer: ByteArray,
            bytesRead: Int,
        ) {
            var index = 0
            var peak = 0
            while (index + 1 < bytesRead) {
                val low = buffer[index].toInt() and BYTE_MASK
                val high = buffer[index + 1].toInt()
                val sample = (high shl BITS_PER_BYTE) or low
                val amplitude =
                    if (sample == Short.MIN_VALUE.toInt()) {
                        Short.MAX_VALUE.toInt()
                    } else {
                        abs(sample)
                    }
                peak = maxOf(peak, amplitude)
                index += BYTES_PER_SAMPLE
            }
            updatePeakAmplitude(peak)
        }

        private fun updatePeakAmplitude(peak: Int) {
            while (peak > maxAmplitude.get()) {
                val current = maxAmplitude.get()
                if (peak <= current || maxAmplitude.compareAndSet(current, peak)) return
            }
        }

        private fun joinRecordingThread() {
            try {
                recordingThread?.join(RECORDING_THREAD_JOIN_TIMEOUT_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Interrupted while waiting for WAV recording thread", e)
            }
        }

        private fun handleOutputFile(isSuccess: Boolean): File? {
            val file = outputFile
            clearRecordingState()

            val isValid =
                isSuccess &&
                    file != null &&
                    file.exists() &&
                    file.length() > WavFileHeader.HEADER_SIZE_BYTES &&
                    finalizeWavHeader(file)

            return if (isValid) {
                file
            } else {
                if (file?.exists() == true) {
                    file.delete()
                }
                null
            }
        }

        private fun finalizeWavHeader(file: File): Boolean {
            val pcmDataSize = file.length() - WavFileHeader.HEADER_SIZE_BYTES
            if (pcmDataSize <= 0) return false

            return try {
                RandomAccessFile(file, "rw").use { wavFile ->
                    wavFile.seek(0)
                    wavFile.write(createWavHeader(pcmDataSize))
                }
                true
            } catch (e: IOException) {
                Log.w(TAG, "Failed to finalize WAV header", e)
                false
            }
        }

        private fun createWavHeader(pcmDataSize: Long): ByteArray =
            WavFileHeader.create(
                pcmDataSize = pcmDataSize,
                sampleRate = SAMPLE_RATE,
                channelCount = CHANNEL_COUNT,
                bitsPerSample = BITS_PER_SAMPLE,
            )

        private fun hasRecordAudioPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        private fun clearRecordingState() {
            audioRecord = null
            outputFile = null
            outputStream = null
            recordingThread = null
            isRecording = false
            maxAmplitude.set(0)
        }

        private fun cleanupFailedStart(
            recorder: AudioRecord?,
            stream: FileOutputStream?,
            file: File,
        ) {
            isRecording = false
            recorder?.release()
            try {
                stream?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close WAV output stream after start failure", e)
            }
            if (file.exists()) {
                file.delete()
            }
            clearRecordingState()
        }

        /**
         * 현재 생성된 파일 반환
         */
        fun getOutputFile(): File? = outputFile

        /**
         * 리소스 해제
         */
        fun release() {
            stop()
        }

        companion object {
            private const val TAG = "AndroidAudioRecorder"
            private const val RECORDING_THREAD_NAME = "CloudSttWavRecorder"
            private const val SAMPLE_RATE = 16000
            private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
            private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
            private const val CHANNEL_COUNT = 1
            private const val BITS_PER_SAMPLE = 16
            private const val BYTES_PER_SAMPLE = 2
            private const val BITS_PER_BYTE = 8
            private const val BYTE_MASK = 0xFF
            private const val MIN_RECORDING_BUFFER_BYTES = 3200
            private const val RECORDING_THREAD_JOIN_TIMEOUT_MS = 1000L
        }
    }
