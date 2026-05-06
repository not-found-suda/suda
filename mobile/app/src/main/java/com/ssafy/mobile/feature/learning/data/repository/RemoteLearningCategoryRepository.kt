package com.ssafy.mobile.feature.learning.data.repository

import android.util.Log
import com.ssafy.mobile.feature.learning.data.api.LearningCategoryApiService
import com.ssafy.mobile.feature.learning.domain.model.LearningCategory
import com.ssafy.mobile.feature.learning.domain.repository.LearningCategoryRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException

private const val HTTP_STATUS_UNAUTHORIZED = 401

class RemoteLearningCategoryRepository
    @Inject
    constructor(
        private val apiService: LearningCategoryApiService,
    ) : LearningCategoryRepository {
        override suspend fun getCategories(): List<LearningCategory> =
            try {
                val response = apiService.getCategories()
                response.categories.map { it.toDomain() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e("RemoteLearningCategory", "HttpException: ${e.code()}", e)
                val message =
                    when (e.code()) {
                        HTTP_STATUS_UNAUTHORIZED -> "세션이 만료되었습니다. 다시 로그인해 주세요."
                        else -> "카테고리를 불러오지 못했습니다. 다시 시도해 주세요."
                    }
                error(message)
            } catch (e: IOException) {
                Log.e("RemoteLearningCategory", "IOException", e)
                error("네트워크 연결을 확인해 주세요.")
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e("RemoteLearningCategory", "Unknown Exception", e)
                error("카테고리를 불러오는 중 오류가 발생했습니다.")
            }
    }
