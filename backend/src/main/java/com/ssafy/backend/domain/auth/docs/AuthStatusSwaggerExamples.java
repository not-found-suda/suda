package com.ssafy.backend.domain.auth.docs;

public final class AuthStatusSwaggerExamples {

  private AuthStatusSwaggerExamples() {}

  public static final String AUTH_STATUS_SUCCESS =
      """
        {
          "message": "인증되었습니다."
        }
        """;

  public static final String UNAUTHORIZED =
      """
        {
          "type": "urn:s14p31a404:error:common-unauthorized",
          "title": "인증이 필요합니다.",
          "status": 401,
          "detail": "인증이 필요합니다.",
          "instance": "/api/v1/auth/status",
          "code": "COMMON_UNAUTHORIZED",
          "traceId": "9f8d7c6b5a4e3210"
        }
        """;

  public static final String FORBIDDEN =
      """
        {
          "type": "urn:s14p31a404:error:common-forbidden",
          "title": "접근 권한이 없습니다.",
          "status": 403,
          "detail": "접근 권한이 없습니다.",
          "instance": "/api/v1/auth/status",
          "code": "COMMON_FORBIDDEN",
          "traceId": "9f8d7c6b5a4e3210"
        }
        """;

  public static final String INTERNAL_SERVER_ERROR =
      """
        {
          "type": "urn:s14p31a404:error:common-internal-server-error",
          "title": "서버 내부 오류가 발생했습니다.",
          "status": 500,
          "detail": "서버 내부 오류가 발생했습니다.",
          "instance": "/api/v1/auth/status",
          "code": "COMMON_INTERNAL_SERVER_ERROR",
          "traceId": "9f8d7c6b5a4e3210"
        }
        """;
}
