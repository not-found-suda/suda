package com.ssafy.mobile.core.session.di

import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.core.session.DataStoreActiveChildStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindActiveChildStorage(
        dataStoreActiveChildStorage: DataStoreActiveChildStorage,
    ): ActiveChildStorage
}
