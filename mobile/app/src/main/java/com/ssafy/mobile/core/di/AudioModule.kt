package com.ssafy.mobile.core.di

import com.ssafy.mobile.core.audio.AndroidAudioPlayer
import com.ssafy.mobile.core.audio.AndroidSystemTtsPlayer
import com.ssafy.mobile.core.audio.AudioPlayer
import com.ssafy.mobile.core.audio.TtsPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindAudioPlayer(androidAudioPlayer: AndroidAudioPlayer): AudioPlayer

    @Binds
    @Singleton
    abstract fun bindTtsPlayer(androidSystemTtsPlayer: AndroidSystemTtsPlayer): TtsPlayer
}
