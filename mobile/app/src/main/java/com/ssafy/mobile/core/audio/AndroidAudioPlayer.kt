package com.ssafy.mobile.core.audio

import android.content.Context
import android.media.MediaPlayer
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
                        prepareAsync()
                    }
            }.onFailure {
                // 에러 로깅 처리 필요 시 추가
                it.printStackTrace()
                stop()
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
