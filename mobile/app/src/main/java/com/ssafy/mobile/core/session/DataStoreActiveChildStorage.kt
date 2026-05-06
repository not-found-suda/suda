package com.ssafy.mobile.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.activeChildDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "active_child_prefs",
)

class DataStoreActiveChildStorage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ActiveChildStorage {
        companion object {
            private val KEY_ACTIVE_CHILD_ID = longPreferencesKey("active_child_id")
        }

        override suspend fun getActiveChildId(): Long? =
            context.activeChildDataStore.data
                .map { preferences -> preferences[KEY_ACTIVE_CHILD_ID] }
                .first()

        override suspend fun saveActiveChildId(childId: Long) {
            context.activeChildDataStore.edit { preferences ->
                preferences[KEY_ACTIVE_CHILD_ID] = childId
            }
        }

        override suspend fun clearActiveChildId() {
            context.activeChildDataStore.edit { preferences ->
                preferences.remove(KEY_ACTIVE_CHILD_ID)
            }
        }
    }
