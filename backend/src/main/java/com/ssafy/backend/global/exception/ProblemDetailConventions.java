package com.ssafy.backend.global.exception;

import org.springframework.http.HttpStatus;

public final class ProblemDetailConventions {

  private ProblemDetailConventions() {}

  public static String resolveTitle(ErrorCode errorCode) {
    if (errorCode == null) {
      return "서비스 오류";
    }

    String overrideTitle = errorCode.getTitleOverride();
    if (overrideTitle != null && !overrideTitle.isBlank()) {
      return overrideTitle;
    }

    String domainTitle = errorCode.getDomainTitle();
    if (domainTitle != null && !domainTitle.isBlank()) {
      return domainTitle;
    }

    return resolveStatusTitle(errorCode.getHttpStatus());
  }

  private static String resolveStatusTitle(HttpStatus status) {
    if (status == null) {
      return "서비스 오류";
    }
    return switch (status) {
      case BAD_REQUEST -> "잘못된 요청";
      case UNAUTHORIZED -> "인증 필요";
      case FORBIDDEN -> "접근 권한 없음";
      case NOT_FOUND -> "리소스를 찾을 수 없음";
      case CONFLICT -> "요청 충돌";
      case INTERNAL_SERVER_ERROR -> "서버 내부 오류";
      default -> "서비스 오류";
    };
  }

  public static String normalizeInstance(String instance) {
    if (instance == null || instance.isBlank()) {
      return "/";
    }
    if (instance.startsWith("/")) {
      return instance;
    }
    return "/" + instance;
  }
}
