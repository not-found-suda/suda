package com.ssafy.mobile.feature.childprofile.domain.repository

import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile

interface ChildProfileRepository {
    suspend fun getChildProfiles(): List<ChildProfile>

    suspend fun createChildProfile(
        name: String,
        age: Int,
    )
}
