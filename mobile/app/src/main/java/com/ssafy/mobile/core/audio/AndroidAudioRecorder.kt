package com.ssafy.mobile.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android MediaRecorder를 사용하여 오디오를 파일로 녹음하는 클래스
 */
@Singleton
class AndroidAudioRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var recorder: MediaRecorder? = null
        private var outputFile: File? = null

        /**
         * 녹음 시작
         * @param fileName 저장할 파일 이름 (확장자 제외)
         */
        fun start(fileName: String = "stt_audio") {
            if (recorder != null) stop()

            outputFile =
                File(context.cacheDir, "$fileName.m4a").apply {
                    if (exists()) delete()
                }

            recorder =
                createRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(SAMPLING_RATE)
                    setAudioEncodingBitRate(BIT_RATE)
                    setOutputFile(outputFile!!.absolutePath)

                    prepare()
                    start()
                }
        }

        private fun createRecorder(): MediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        /**
         * 녹음 중지 및 결과 파일 반환
         */
        @Suppress("TooGenericExceptionCaught")
        fun stop(): File? {
            var isSuccess = true
            recorder?.let { rec ->
                try {
                    rec.stop()
                    rec.release()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    isSuccess = false
                } catch (e: RuntimeException) {
                    // 녹음이 너무 짧아 stop failed 발생 시 처리
                    e.printStackTrace()
                    isSuccess = false
                }
            }
            recorder = null

            return handleOutputFile(isSuccess)
        }

        private fun handleOutputFile(isSuccess: Boolean): File? {
            val file = outputFile
            val isValid = isSuccess && file != null && file.exists() && file.length() > 0

            return if (isValid) {
                file
            } else {
                // 실패하거나 유효하지 않은 파일인 경우 삭제 후 null 반환
                if (file?.exists() == true) {
                    file.delete()
                }
                outputFile = null
                null
            }
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
            private const val SAMPLING_RATE = 44100
            private const val BIT_RATE = 128000
        }
    }
