package com.ssafy.mobile.data.local.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.mobileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mobile_preferences"
)

class MobilePreferenceDataSource(
    private val dataStore: DataStore<Preferences>
) {
    val lastSyncAt: Flow<Long?> = dataStore.data
        .map { preferences -> preferences[LAST_SYNC_AT] }
        .distinctUntilChanged()

    val lastSyncedTodoId: Flow<Int?> = dataStore.data
        .map { preferences -> preferences[LAST_SYNC_TODO_ID] }
        .distinctUntilChanged()

    suspend fun updateSyncInfo(todoId: Int, syncedAt: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TODO_ID] = todoId
            preferences[LAST_SYNC_AT] = syncedAt
        }
    }

    companion object {
        private val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        private val LAST_SYNC_TODO_ID = intPreferencesKey("last_sync_todo_id")

        fun create(context: Context): MobilePreferenceDataSource =
            MobilePreferenceDataSource(context.mobileDataStore)
    }
}
