package com.ssafy.mobile.feature.childprofile.domain

import android.util.Log
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.childprofile.domain.repository.ChildProfileRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface ActiveChildProfileState {
    data object Loading : ActiveChildProfileState

    data object Missing : ActiveChildProfileState

    data object NotFound : ActiveChildProfileState

    data class Selected(
        val profile: ChildProfile,
    ) : ActiveChildProfileState

    data class Error(
        val message: String,
    ) : ActiveChildProfileState
}

@Singleton
class ActiveChildProfileManager
    @Inject
    constructor(
        private val activeChildStorage: ActiveChildStorage,
        private val childProfileRepository: ChildProfileRepository,
    ) {
        companion object {
            private const val TAG = "ActiveChildProfileManager"
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun getActiveChildProfile(): ActiveChildProfileState {
            return try {
                val activeChildId =
                    activeChildStorage.getActiveChildId()
                        ?: return ActiveChildProfileState.Missing

                val profiles = childProfileRepository.getChildProfiles()
                val activeProfile = profiles.find { it.childId == activeChildId }

                if (activeProfile != null) {
                    ActiveChildProfileState.Selected(activeProfile)
                } else {
                    ActiveChildProfileState.NotFound
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Network error while fetching active child profile", e)
                ActiveChildProfileState.Error("네트워크 오류가 발생했습니다.")
            } catch (e: Exception) {
                // 알 수 없는 다양한 런타임 예외를 UI 상태로 변환하여 크래시 방지
                Log.e(TAG, "Unknown error while fetching active child profile", e)
                ActiveChildProfileState.Error("아이 정보를 불러오지 못했습니다.")
            }
        }
    }
