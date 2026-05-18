package com.ssafy.mobile.feature.childprofile.domain.repository

import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile

interface ChildProfileRepository {
    suspend fun getChildProfiles(): List<ChildProfile>

    suspend fun createChildProfile(
        name: String,
        birthDate: String,
        avatarKey: String,
    )

    suspend fun getChildProfile(childId: Long): ChildProfile

    suspend fun updateChildProfile(
        childId: Long,
        name: String?,
        birthDate: String?,
        avatarKey: String?,
    )

    suspend fun deleteChildProfile(childId: Long)
}
