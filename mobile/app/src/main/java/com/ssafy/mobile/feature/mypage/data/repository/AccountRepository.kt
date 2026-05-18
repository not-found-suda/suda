package com.ssafy.mobile.feature.mypage.data.repository

import android.util.Log
import com.ssafy.mobile.feature.mypage.data.api.AccountApiService
import com.ssafy.mobile.feature.mypage.data.dto.AccountUpdateRequestDto
import com.ssafy.mobile.feature.mypage.domain.model.AccountInfo
import com.ssafy.mobile.feature.mypage.domain.model.AccountUpdateResult
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

        private fun accountErrorMessage(
            statusCode: Int,
            fallback: String,
        ): String =
            when (statusCode) {
                HTTP_STATUS_BAD_REQUEST -> "입력 정보를 확인해 주세요."
                HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다. 다시 로그인해 주세요."
                HTTP_STATUS_NOT_FOUND -> "사용자 정보를 찾을 수 없습니다."
                else -> fallback
            }
    }
