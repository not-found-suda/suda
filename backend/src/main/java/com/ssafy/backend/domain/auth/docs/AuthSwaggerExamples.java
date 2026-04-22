package com.ssafy.backend.domain.auth.docs;

public final class AuthSwaggerExamples {

  private AuthSwaggerExamples() {}

  public static final String SIGNUP_SUCCESS =
      """
        {
          "userId": 1,
          "email": "user1@test.com"
        }
        """;

  public static final String LOGIN_SUCCESS =
      """
        {
          "accessToken": "<JWT>",
          "tokenType": "Bearer",
          "expiresIn": 900
        }
        """;

  public static final String REFRESH_SUCCESS =
      """
        {
          "accessToken": "<JWT>",
          "tokenType": "Bearer",
          "expiresIn": 900
        }
        """;

  public static final String INVALID_REFRESH_TOKEN =
      """
        {
          "type": "urn:s14p31a404:error:auth-invalid-refresh-token",
          "title": "유효하지 않은 리프레시 토큰입니다.",
          "status": 401,
          "detail": "유효하지 않은 리프레시 토큰입니다.",
          "instance": "/api/v1/auth/refresh",
          "code": "AUTH_INVALID_REFRESH_TOKEN",
          "traceId": "9f8d7c6b5a4e3210"
        }
        """;
}
