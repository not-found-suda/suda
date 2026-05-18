package com.ssafy.mobile.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
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
        @param:ApplicationContext private val context: Context,
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
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                            onComplete()
                            stop()
                            true
                        }
                        prepareAsync()
                    }
            }.onFailure {
                Log.e(TAG, "Failed to play audio data", it)
                onComplete()
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
                Log.e(TAG, "Failed to decode base64 audio", it)
                onError()
            }
        }

        override fun playUrl(
            url: String,
            onPrepared: () -> Unit,
            onComplete: () -> Unit,
            onError: (String) -> Unit,
        ) {
            stop()

            if (url.isBlank()) {
                onError("재생할 오디오 URL이 없습니다.")
                return
            }

            runCatching {
                mediaPlayer =
                    MediaPlayer().apply {
                        setDataSource(url)
                        setOnPreparedListener {
                            onPrepared()
                            start()
                        }
                        setOnCompletionListener {
                            onComplete()
                            stop()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "MediaPlayer URL playback error: what=$what, extra=$extra")
                            onError("오디오를 재생하지 못했습니다. (code: $what)")
                            stop()
                            true
                        }
                        prepareAsync()
                    }
            }.onFailure {
                Log.e(TAG, "Failed to initialize URL playback", it)
                onError("오디오 재생 준비 중 오류가 발생했습니다.")
                stop()
            }
        }

        override fun stop() {
            mediaPlayer?.let {
                runCatching {
                    if (it.isPlaying) it.stop()
                    it.release()
                }.onFailure { e ->
                    Log.w(TAG, "Error while stopping/releasing MediaPlayer", e)
                }
            }
            mediaPlayer = null

            // 사용한 임시 파일 삭제
            tempAudioFile?.delete()
            tempAudioFile = null
        }

        override fun release() {
            stop()
        }

        companion object {
            private const val TAG = "AndroidAudioPlayer"
        }
    }
