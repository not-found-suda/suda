package com.ssafy.mobile.core.di

import com.ssafy.mobile.core.audio.AndroidAudioPlayer
import com.ssafy.mobile.core.audio.AudioPlayer
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
}
