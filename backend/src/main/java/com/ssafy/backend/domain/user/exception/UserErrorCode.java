package com.ssafy.backend.domain.user.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
  USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  UserErrorCode(String code, HttpStatus httpStatus, String message) {
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
    return "사용자 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
