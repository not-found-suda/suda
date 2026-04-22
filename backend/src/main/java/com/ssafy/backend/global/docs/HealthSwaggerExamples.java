package com.ssafy.backend.global.docs;

public final class HealthSwaggerExamples {

  private HealthSwaggerExamples() {}

  public static final String HEALTH_SUCCESS =
      """
        {
          "status": "UP"
        }
        """;

  public static final String INTERNAL_SERVER_ERROR =
      """
        {
          "type": "urn:s14p31a404:error:common-internal-server-error",
          "title": "서버 내부 오류가 발생했습니다.",
          "status": 500,
          "detail": "서버 내부 오류가 발생했습니다.",
          "instance": "/api/v1/health",
          "code": "COMMON_INTERNAL_SERVER_ERROR",
          "traceId": "9f8d7c6b5a4e3210"
        }
        """;
}
