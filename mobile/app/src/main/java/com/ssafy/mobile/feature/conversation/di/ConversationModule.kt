package com.ssafy.mobile.feature.conversation.di

import com.ssafy.mobile.core.vision.SignRecognitionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class ConversationModule {
    @Binds
    @ViewModelScoped
    abstract fun bindSignRecognitionEngine(
        fakeEngine: FakeSignRecognitionEngine,
    ): SignRecognitionEngine
}
