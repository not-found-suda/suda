package com.ssafy.mobile.core.network

/**
 * 백엔드 Refresh API 명세가 확정될 때까지 사용할 임시 인터페이스입니다.
 * // [S14P31A404-226] 명세 확정 시 DTO 및 Endpoint URL 작성 후 Retrofit Service 로 연동.
 * 연동 시 반드시 NetworkModule 에 정의될 NoAuthRetrofit 주입을 사용해야 순환 참조를 막을 수 있습니다.
 */
interface RefreshTokenClient {
    /**
     * @return 새로운 AccessToken (성공 시), 갱신 실패 시 null 반환
     */
    fun refresh(refreshToken: String): String?
}

// API 명세가 나올 때까지 사용할 임시 빈 구현체
class MockRefreshTokenClient : RefreshTokenClient {
    override fun refresh(refreshToken: String): String? {
        // [S14P31A404-226] 실제 API 연동 시 이곳을 제거하고 별도의 Retrofit 인터페이스를 주입받아 사용해야 함
        return null
    }
}
