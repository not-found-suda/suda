package com.ssafy.mobile.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android MediaPlayer를 사용한 AudioPlayer 구현체.
 */
@Singleton
class AndroidAudioPlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioPlayer {
        private var mediaPlayer: MediaPlayer? = null
        private var tempAudioFile: File? = null

        override fun play(
            audioData: ByteArray,
            onComplete: () -> Unit,
        ) {
            stop()

            runCatching {
                // 1. 임시 파일 생성
                tempAudioFile =
                    File.createTempFile("tts_cache", ".mp3", context.cacheDir).apply {
                        FileOutputStream(this).use { it.write(audioData) }
                    }

                // 2. MediaPlayer 초기화 및 재생
                mediaPlayer =
                    MediaPlayer().apply {
                        setDataSource(tempAudioFile?.absolutePath)
                        setOnPreparedListener { start() }
                        setOnCompletionListener {
                            onComplete()
                            stop()
                        }
                        setOnErrorListener { _, _, _ ->
                            onComplete() // 에러 시에도 STT 복구를 위해 콜백 호출
                            stop()
                            true
                        }
                        prepareAsync()
                    }
            }.onFailure {
                onComplete() // 파일 생성 등 초기 단계 실패 시에도 콜백 호출
                it.printStackTrace()
                stop()
            }
        }

        override fun playBase64(
            base64Data: String,
            onComplete: () -> Unit,
            onError: () -> Unit,
        ) {
            runCatching {
                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                play(audioBytes, onComplete)
            }.onFailure {
                onError()
                it.printStackTrace()
            }
        }

        override fun stop() {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            // 사용한 임시 파일 삭제
            tempAudioFile?.delete()
            tempAudioFile = null
        }

        override fun release() {
            stop()
        }
    }
