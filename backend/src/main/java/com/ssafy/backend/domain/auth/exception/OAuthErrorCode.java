package com.ssafy.backend.domain.auth.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OAuthErrorCode implements ErrorCode {
  INVALID_PROVIDER_TOKEN(
      "OAUTH_INVALID_PROVIDER_TOKEN", HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 로그인 토큰입니다."),
  EMAIL_ALREADY_EXISTS(
      "OAUTH_EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 같은 이메일로 가입된 계정이 있습니다."),
  PROVIDER_ERROR("OAUTH_PROVIDER_ERROR", HttpStatus.BAD_GATEWAY, "소셜 로그인 제공자 API 호출에 실패했습니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  OAuthErrorCode(String code, HttpStatus httpStatus, String message) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String getDomainTitle() {
    return "OAuth 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
