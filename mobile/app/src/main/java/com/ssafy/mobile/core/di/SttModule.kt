package com.ssafy.mobile.core.di

import com.ssafy.mobile.core.stt.LocalSttEngine
import com.ssafy.mobile.core.stt.SttEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SttModule {
    @Binds
    @Singleton
    abstract fun bindSttEngine(localSttEngine: LocalSttEngine): SttEngine
}
