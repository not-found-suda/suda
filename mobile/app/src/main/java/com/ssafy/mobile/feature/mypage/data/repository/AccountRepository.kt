package com.ssafy.mobile.feature.mypage.data.repository

import android.util.Log
import com.ssafy.mobile.feature.mypage.data.api.AccountApiService
import com.ssafy.mobile.feature.mypage.data.dto.AccountUpdateRequestDto
import com.ssafy.mobile.feature.mypage.data.dto.TtsSpeakerUpdateRequestDto
import com.ssafy.mobile.feature.mypage.domain.model.AccountInfo
import com.ssafy.mobile.feature.mypage.domain.model.AccountUpdateResult
import com.ssafy.mobile.feature.mypage.domain.model.TtsSpeakerOption
import com.ssafy.mobile.feature.mypage.domain.model.TtsSpeakerUpdateResult
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AccountRepository"
private const val HTTP_STATUS_BAD_REQUEST = 400
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_NOT_FOUND = 404

class AccountRepository
    @Inject
    constructor(
        private val apiService: AccountApiService,
    ) {
        suspend fun getAccountInfo(): AccountInfo =
            try {
                val response = apiService.getAccountInfo()
                if (response.isSuccessful) {
                    val body = response.body() ?: error("계정 정보 응답이 비어 있습니다.")
                    body.toDomain()
                } else {
                    error(accountErrorMessage(response.code(), "계정 정보를 불러오지 못했습니다."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "getAccountInfo IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            }

        suspend fun updateName(name: String): AccountUpdateResult =
            try {
                val response =
                    apiService.updateAccountInfo(
                        AccountUpdateRequestDto(name = name),
                    )
                if (response.isSuccessful) {
                    val body = response.body() ?: error("계정 수정 응답이 비어 있습니다.")
                    body.toDomain()
                } else {
                    error(accountErrorMessage(response.code(), "계정 정보를 저장하지 못했습니다."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "updateName IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            }

        suspend fun getTtsSpeakerOptions(): List<TtsSpeakerOption> =
            try {
                val response = apiService.getTtsSpeakers()
                if (response.isSuccessful) {
                    val body = response.body() ?: error("목소리 목록 응답이 비어 있습니다.")
                    body.speakers.map { speaker -> speaker.toDomain() }
                } else {
                    error(accountErrorMessage(response.code(), "목소리 목록을 불러오지 못했습니다."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "getTtsSpeakerOptions IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            }

        suspend fun updateTtsSpeaker(ttsSpeaker: String): TtsSpeakerUpdateResult =
            try {
                val response =
                    apiService.updateTtsSpeaker(
                        TtsSpeakerUpdateRequestDto(ttsSpeaker = ttsSpeaker),
                    )
                if (response.isSuccessful) {
                    val body = response.body() ?: error("목소리 설정 응답이 비어 있습니다.")
                    body.toDomain()
                } else {
                    error(accountErrorMessage(response.code(), "목소리 설정을 저장하지 못했습니다."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "updateTtsSpeaker IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            }

        private fun accountErrorMessage(
            statusCode: Int,
            fallback: String,
        ): String =
            when (statusCode) {
                HTTP_STATUS_BAD_REQUEST -> "입력 정보를 확인해 주세요."
                HTTP_STATUS_UNAUTHORIZED -> "인증이 만료됐습니다. 다시 로그인해 주세요."
                HTTP_STATUS_NOT_FOUND -> "사용자 정보를 찾을 수 없습니다."
                else -> fallback
            }
    }
