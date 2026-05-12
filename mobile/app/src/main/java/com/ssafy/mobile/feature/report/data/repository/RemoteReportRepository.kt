package com.ssafy.mobile.feature.report.data.repository

import android.util.Log
import com.ssafy.mobile.feature.report.data.api.ReportApiService
import com.ssafy.mobile.feature.report.data.dto.toDomain
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgressPage
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionPage
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage
import com.ssafy.mobile.feature.report.domain.repository.ReportRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "RemoteReportRepository"
private const val HTTP_STATUS_BAD_REQUEST = 400
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_INTERNAL_SERVER_ERROR = 500

class RemoteReportRepository
    @Inject
    constructor(
        private val apiService: ReportApiService,
    ) : ReportRepository {
        override suspend fun getSummary(childId: Long): Result<ReportSummary> =
            try {
                val response = apiService.getSummary(childId)

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(IllegalStateException("리포트 요약 응답이 비어 있습니다."))
                    Result.success(body.toDomain())
                } else {
                    Result.failure(
                        IllegalStateException(
                            errorMessage(
                                statusCode = response.code(),
                                defaultMessage = "리포트 요약을 불러오지 못했습니다.",
                            ),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Report summary network error", e)
                Result.failure(IOException("네트워크 연결을 확인해 주세요."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Report summary unknown error", e)
                Result.failure(IllegalStateException("리포트 요약을 불러오는 중 오류가 발생했습니다."))
            }

        override suspend fun getWeakWords(
            childId: Long,
            page: Int,
            size: Int,
        ): Result<ReportWeakWordPage> =
            try {
                val response =
                    apiService.getWeakWords(
                        childId = childId,
                        page = page,
                        size = size,
                    )

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(IllegalStateException("취약 단어 응답이 비어 있습니다."))
                    Result.success(body.toDomain())
                } else {
                    Result.failure(
                        IllegalStateException(
                            errorMessage(
                                statusCode = response.code(),
                                defaultMessage = "취약 단어를 불러오지 못했습니다.",
                            ),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Report weak words network error", e)
                Result.failure(IOException("네트워크 연결을 확인해 주세요."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Report weak words unknown error", e)
                Result.failure(IllegalStateException("취약 단어를 불러오는 중 오류가 발생했습니다."))
            }

        override suspend fun getCategoryProgress(
            childId: Long,
            from: String?,
            to: String?,
        ): Result<ReportCategoryProgressPage> =
            try {
                val response =
                    apiService.getCategoryProgress(
                        childId = childId,
                        from = from,
                        to = to,
                    )

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(IllegalStateException("카테고리 리포트 응답이 비어 있습니다."))
                    Result.success(body.toDomain())
                } else {
                    Result.failure(
                        IllegalStateException(
                            errorMessage(
                                statusCode = response.code(),
                                defaultMessage = "카테고리별 리포트를 불러오지 못했습니다.",
                            ),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Report category progress network error", e)
                Result.failure(IOException("네트워크 연결을 확인해 주세요."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Report category progress unknown error", e)
                Result.failure(IllegalStateException("카테고리별 리포트를 불러오는 중 오류가 발생했습니다."))
            }

        override suspend fun getQuizSessions(
            childId: Long,
            page: Int,
            size: Int,
        ): Result<ReportQuizSessionPage> =
            try {
                val response =
                    apiService.getQuizSessions(
                        childId = childId,
                        page = page,
                        size = size,
                    )

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(IllegalStateException("퀴즈 기록 응답이 비어 있습니다."))
                    Result.success(body.toDomain())
                } else {
                    Result.failure(
                        IllegalStateException(
                            errorMessage(
                                statusCode = response.code(),
                                defaultMessage = "퀴즈 기록을 불러오지 못했습니다.",
                            ),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Report quiz sessions network error", e)
                Result.failure(IOException("네트워크 연결을 확인해 주세요."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Report quiz sessions unknown error", e)
                Result.failure(IllegalStateException("퀴즈 기록을 불러오는 중 오류가 발생했습니다."))
            }

        private fun errorMessage(
            statusCode: Int,
            defaultMessage: String,
        ): String =
            when (statusCode) {
                HTTP_STATUS_BAD_REQUEST -> "리포트 요청 값이 올바르지 않습니다."
                HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다. 다시 로그인해 주세요."
                HTTP_STATUS_NOT_FOUND -> "아이 정보를 찾을 수 없습니다. 아이를 다시 선택해 주세요."
                HTTP_STATUS_INTERNAL_SERVER_ERROR -> "서버에서 리포트 정보를 불러오지 못했습니다."
                else -> defaultMessage
            }
    }
