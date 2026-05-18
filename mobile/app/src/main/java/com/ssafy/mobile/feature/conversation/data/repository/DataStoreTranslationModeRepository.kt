package com.ssafy.mobile.feature.conversation.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import com.ssafy.mobile.feature.conversation.domain.repository.TranslationModeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.translationModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "translation_mode_prefs",
)

class DataStoreTranslationModeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : TranslationModeRepository {
        override val translationMode: Flow<TranslationMode> =
            context.translationModeDataStore.data
                .map { preferences ->
                    TranslationMode.fromStorageValue(preferences[KEY_TRANSLATION_MODE])
                }.distinctUntilChanged()

        override suspend fun saveTranslationMode(mode: TranslationMode) {
            context.translationModeDataStore.edit { preferences ->
                preferences[KEY_TRANSLATION_MODE] = mode.name
            }
        }

        private companion object {
            val KEY_TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        }
    }
