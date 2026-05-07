package com.ssafy.mobile.core.network

import com.ssafy.mobile.core.network.dto.RefreshRequestDto
import com.ssafy.mobile.core.network.dto.RefreshResponseDto
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 토큰 갱신을 담당하는 클라이언트 인터페이스입니다.
 */
interface RefreshTokenClient {
    suspend fun refresh(refreshToken: String): RefreshResponseDto?
}

/**
 * 실제 백엔드 API와 통신하여 토큰을 갱신하는 Retrofit 기반 구현체입니다.
 */
class RetrofitRefreshTokenClient(
    private val noAuthRetrofit: Retrofit,
) : RefreshTokenClient {
    private interface RefreshApiService {
        @POST("v1/auth/refresh")
        suspend fun refresh(
            @Body request: RefreshRequestDto,
        ): Response<RefreshResponseDto>
    }

    private val apiService = noAuthRetrofit.create(RefreshApiService::class.java)

    override suspend fun refresh(refreshToken: String): RefreshResponseDto? =
        try {
            val response = apiService.refresh(RefreshRequestDto(refreshToken))
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            android.util.Log.e(
                "RefreshTokenClient",
                "Network error during refresh: ${e.message}",
                e,
            )
            null
        } catch (e: IllegalStateException) {
            android.util.Log.e(
                "RefreshTokenClient",
                "Invalid state during refresh: ${e.message}",
                e,
            )
            null
        } catch (e: com.google.gson.JsonSyntaxException) {
            android.util.Log.e("RefreshTokenClient", "Response parsing failed: ${e.message}", e)
            null
        }
}

/**
 * 테스트용 Mock 구현체입니다.
 */
class MockRefreshTokenClient : RefreshTokenClient {
    override suspend fun refresh(refreshToken: String): RefreshResponseDto? = null
}
