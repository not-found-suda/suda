package com.ssafy.mobile.core.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.appThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_theme_prefs",
)

@Singleton
class AppThemeModeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val themeMode: Flow<AppThemeMode> =
            context.appThemeDataStore.data
                .map { preferences ->
                    AppThemeMode.fromStorageValue(preferences[KEY_THEME_MODE])
                }.distinctUntilChanged()

        suspend fun saveThemeMode(mode: AppThemeMode) {
            context.appThemeDataStore.edit { preferences ->
                preferences[KEY_THEME_MODE] = mode.name
            }
        }

        private companion object {
            val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        }
    }
