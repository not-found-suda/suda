package com.ssafy.mobile.feature.conversation.di

import android.content.Context
import com.ssafy.mobile.core.vision.RealSignRecognitionEngine
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.TfliteSignInferenceAdapter
import com.ssafy.mobile.core.vision.wordspotting.NoOpWordSpottingScanner
import com.ssafy.mobile.core.vision.wordspotting.WordSpottingScanner
import com.ssafy.mobile.feature.conversation.data.remote.TranslateApiService
import com.ssafy.mobile.feature.conversation.data.repository.DataStoreTranslationModeRepository
import com.ssafy.mobile.feature.conversation.data.repository.DefaultTranslateRepository
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import com.ssafy.mobile.feature.conversation.domain.repository.TranslationModeRepository
import com.ssafy.mobile.translation.OnDeviceTranslationEngine
import com.ssafy.mobile.translation.QwenLiteRtTranslationEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class ConversationModule {
    @Binds
    @Singleton
    abstract fun bindTranslateRepository(
        repository: DefaultTranslateRepository,
    ): TranslateRepository

    @Binds
    @Singleton
    abstract fun bindTranslationModeRepository(
        repository: DataStoreTranslationModeRepository,
    ): TranslationModeRepository

    @Binds
    @Singleton
    abstract fun bindSignRecognitionEngine(
        realEngine: RealSignRecognitionEngine,
    ): SignRecognitionEngine

    companion object {
        @Provides
        @Singleton
        fun provideTranslateApiService(
            @Named("NoAuth") retrofit: Retrofit,
        ): TranslateApiService = retrofit.create(TranslateApiService::class.java)

        @Provides
        @Singleton
        fun provideSignInferenceAdapter(
            @ApplicationContext context: Context,
        ): SignInferenceAdapter = TfliteSignInferenceAdapter.createOrFallback(context)

        @Provides
        @Singleton
        fun provideWordSpottingScanner(): WordSpottingScanner = NoOpWordSpottingScanner

        @Provides
        @Singleton
        fun provideOnDeviceTranslationEngine(
            @ApplicationContext context: Context,
        ): OnDeviceTranslationEngine = QwenLiteRtTranslationEngine(context)
    }
}
