package com.ssafy.mobile.feature.childprofile.data.repository

import com.ssafy.mobile.BuildConfig
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.childprofile.domain.repository.ChildProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * 실제 API 연동 전까지 사용하는 Fake Repository 구현체입니다.
 * [S14P31A404-234] 아이 프로필 목록 및 선택 화면 개발용 데이터 제공용.
 */
@Singleton
class FakeChildProfileRepository
    @Inject
    constructor() : ChildProfileRepository {
        // 메모리 기반 임시 저장소 (백엔드 API 연동 전까지 유지)
        private val createdProfiles = mutableListOf<ChildProfile>()

        override suspend fun getChildProfiles(): List<ChildProfile> {
            // 네트워크 지연 시뮬레이션
            delay(LOADING_DELAY_MS)

            // 후속 작업: [S14P31A404-300] 백엔드 아이 프로필 API 완성 시 RemoteRepository 구현체로 교체 필요
            val baseProfiles =
                if (BuildConfig.DEBUG && USE_SAMPLE_DATA) {
                    listOf(
                        ChildProfile(childId = 1L, name = "민준", age = 6),
                        ChildProfile(childId = 2L, name = "서연", age = 4),
                    )
                } else {
                    emptyList()
                }

            return baseProfiles + createdProfiles
        }

        override suspend fun createChildProfile(
            name: String,
            age: Int,
        ) {
            // 네트워크 지연 시뮬레이션
            delay(LOADING_DELAY_MS)

            // 임시 ID 생성 (기존 프로필 ID와 겹치지 않게 1000L부터 시작)
            val newId = INITIAL_FAKE_ID + createdProfiles.size
            val newProfile =
                ChildProfile(
                    childId = newId,
                    name = name,
                    age = age,
                )
            createdProfiles.add(newProfile)
        }

        companion object {
            private const val LOADING_DELAY_MS = 1000L
            private const val INITIAL_FAKE_ID = 1000L

            /**
             * 개발/검증용 샘플 데이터 노출 여부.
             * 실제 백엔드 연동 전까지 빈 상태(Empty) 테스트를 위해 기본값은 false입니다.
             * 목록 UI 확인이 필요할 때만 로컬에서 true로 변경하여 사용합니다.
             */
            private const val USE_SAMPLE_DATA = false
        }
    }
