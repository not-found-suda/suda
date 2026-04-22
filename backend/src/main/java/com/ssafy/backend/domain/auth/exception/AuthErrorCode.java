package com.ssafy.backend.domain.auth.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
  INVALID_CREDENTIALS(
      "AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
  INVALID_REFRESH_TOKEN(
      "AUTH_INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
  INACTIVE_ACCOUNT("AUTH_INACTIVE_ACCOUNT", HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
  DUPLICATE_EMAIL("AUTH_DUPLICATE_EMAIL", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  AuthErrorCode(String code, HttpStatus httpStatus, String message) {
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
  public String getMessage() {
    return message;
  }
}
