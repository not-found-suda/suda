package com.ssafy.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
  INVALID_REQUEST("COMMON_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
  UNAUTHORIZED("COMMON_UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  FORBIDDEN("COMMON_FORBIDDEN", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  NOT_FOUND("COMMON_NOT_FOUND", HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
  INTERNAL_SERVER_ERROR(
      "COMMON_INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  CommonErrorCode(String code, HttpStatus httpStatus, String message) {
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
