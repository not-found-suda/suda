package com.ssafy.mobile.feature.learning.data.repository

import android.util.Log
import com.ssafy.mobile.feature.learning.data.api.LearningWordApiService
import com.ssafy.mobile.feature.learning.data.dto.toDomain
import com.ssafy.mobile.feature.learning.domain.model.LearningWord
import com.ssafy.mobile.feature.learning.domain.repository.LearningWordRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "RemoteLearningWordRepo"
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_FORBIDDEN = 403

class RemoteLearningWordRepository
    @Inject
    constructor(
        private val apiService: LearningWordApiService,
    ) : LearningWordRepository {
        override suspend fun getWords(
            categoryId: Long,
            difficulty: String,
        ): Result<List<LearningWord>> =
            try {
                val response =
                    apiService.getWords(
                        categoryId = categoryId,
                        difficulty = difficulty,
                    )

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(IllegalStateException("단어 목록 응답이 비어 있습니다."))
                    Result.success(body.words.map { it.toDomain() })
                } else {
                    val errorCode = response.code()
                    val userMessage =
                        when (errorCode) {
                            HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN ->
                                "로그인이 필요하거나 세션이 만료되었습니다."
                            else -> "단어 목록을 불러오지 못했습니다."
                        }
                    Result.failure(IllegalStateException(userMessage))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Word list network error", e)
                Result.failure(IOException("네트워크 연결을 확인해 주세요."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Word list unknown error", e)
                Result.failure(IllegalStateException("단어 목록을 불러오는 중 오류가 발생했습니다."))
            }
    }
