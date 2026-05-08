package com.ssafy.mobile.feature.childprofile.data.repository

import android.util.Log
import com.ssafy.mobile.feature.childprofile.data.api.ChildProfileApiService
import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileCreateRequestDto
import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileUpdateRequestDto
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.childprofile.domain.repository.ChildProfileRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException

private const val TAG = "RemoteChildProfile"
private const val HTTP_STATUS_BAD_REQUEST = 400
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_CONFLICT = 409

@Suppress("TooGenericExceptionCaught")
class RemoteChildProfileRepository
    @Inject
    constructor(
        private val apiService: ChildProfileApiService,
    ) : ChildProfileRepository {
        override suspend fun getChildProfiles(): List<ChildProfile> =
            try {
                val response = apiService.getChildProfiles()
                if (response.isSuccessful) {
                    val body = response.body() ?: error("아이 목록 응답이 비어 있습니다.")
                    body.children.map { it.toDomain() }
                } else {
                    throw HttpException(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "getChildProfiles HttpException: ${e.code()}", e)
                val message =
                    when (e.code()) {
                        HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다. 다시 로그인해 주세요."
                        else -> "아이 목록을 불러오지 못했습니다."
                    }
                error(message)
            } catch (e: IOException) {
                Log.e(TAG, "getChildProfiles IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "getChildProfiles IllegalStateException", e)
                error(e.message ?: "목록을 불러오는 중 오류가 발생했습니다.")
            } catch (e: Exception) {
                Log.e(TAG, "getChildProfiles Unknown Exception", e)
                error("목록을 불러오는 중 오류가 발생했습니다.")
            }

        override suspend fun createChildProfile(
            name: String,
            birthDate: String,
        ) {
            try {
                val request =
                    ChildProfileCreateRequestDto(
                        name = name,
                        birthDate = birthDate,
                    )
                val response = apiService.createChildProfile(request)
                if (!response.isSuccessful) {
                    throw HttpException(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "createChildProfile HttpException: ${e.code()}", e)
                val message =
                    when (e.code()) {
                        HTTP_STATUS_BAD_REQUEST -> "입력 형식이 올바르지 않습니다."
                        HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다."
                        HTTP_STATUS_CONFLICT -> "같은 이름의 아이가 이미 등록되어 있습니다."
                        else -> "아이 프로필을 생성하지 못했습니다."
                    }
                error(message)
            } catch (e: IOException) {
                Log.e(TAG, "createChildProfile IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            } catch (e: Exception) {
                Log.e(TAG, "createChildProfile Unknown Exception", e)
                error("프로필 생성 중 오류가 발생했습니다.")
            }
        }

        override suspend fun getChildProfile(childId: Long): ChildProfile =
            try {
                val response = apiService.getChildProfile(childId)
                if (response.isSuccessful) {
                    val body = response.body() ?: error("아이 상세 응답이 비어 있습니다.")
                    body.toDomain()
                } else {
                    throw HttpException(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "getChildProfile HttpException: ${e.code()}", e)
                val message =
                    when (e.code()) {
                        HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다."
                        HTTP_STATUS_NOT_FOUND -> "존재하지 않거나 삭제된 프로필입니다."
                        else -> "아이 상세 정보를 불러오지 못했습니다."
                    }
                error(message)
            } catch (e: IOException) {
                Log.e(TAG, "getChildProfile IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            } catch (e: Exception) {
                Log.e(TAG, "getChildProfile Unknown Exception", e)
                error("상세 정보를 불러오는 중 오류가 발생했습니다.")
            }

        override suspend fun updateChildProfile(
            childId: Long,
            name: String?,
            birthDate: String?,
        ) {
            try {
                val request = ChildProfileUpdateRequestDto(name = name, birthDate = birthDate)
                val response = apiService.updateChildProfile(childId, request)
                if (!response.isSuccessful) {
                    throw HttpException(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "updateChildProfile HttpException: ${e.code()}", e)
                val message =
                    when (e.code()) {
                        HTTP_STATUS_BAD_REQUEST -> "입력 형식이 올바르지 않습니다."
                        HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다."
                        HTTP_STATUS_NOT_FOUND -> "존재하지 않거나 삭제된 프로필입니다."
                        HTTP_STATUS_CONFLICT -> "이미 같은 이름의 아이가 등록되어 있습니다."
                        else -> "아이 프로필을 수정하지 못했습니다."
                    }
                error(message)
            } catch (e: IOException) {
                Log.e(TAG, "updateChildProfile IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            } catch (e: Exception) {
                Log.e(TAG, "updateChildProfile Unknown Exception", e)
                error("프로필 수정 중 오류가 발생했습니다.")
            }
        }

        override suspend fun deleteChildProfile(childId: Long) {
            try {
                val response = apiService.deleteChildProfile(childId)
                if (!response.isSuccessful) {
                    throw HttpException(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "deleteChildProfile HttpException: ${e.code()}", e)
                val message =
                    when (e.code()) {
                        HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다."
                        HTTP_STATUS_NOT_FOUND -> "이미 삭제되었거나 존재하지 않는 프로필입니다."
                        else -> "아이 프로필을 삭제하지 못했습니다."
                    }
                error(message)
            } catch (e: IOException) {
                Log.e(TAG, "deleteChildProfile IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            } catch (e: Exception) {
                Log.e(TAG, "deleteChildProfile Unknown Exception", e)
                error("프로필 삭제 중 오류가 발생했습니다.")
            }
        }
    }
