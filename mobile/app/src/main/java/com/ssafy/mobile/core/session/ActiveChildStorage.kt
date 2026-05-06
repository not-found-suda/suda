package com.ssafy.mobile.core.session

interface ActiveChildStorage {
    suspend fun getActiveChildId(): Long?

    suspend fun saveActiveChildId(childId: Long)

    suspend fun clearActiveChildId()
}
