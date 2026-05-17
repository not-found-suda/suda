package com.ssafy.backend.domain.user.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
  USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
  CURRENT_PASSWORD_MISMATCH(
      "USER_CURRENT_PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
  NEW_PASSWORD_SAME_AS_CURRENT(
      "USER_NEW_PASSWORD_SAME_AS_CURRENT", HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
  PASSWORD_LOGIN_NOT_ENABLED(
      "USER_PASSWORD_LOGIN_NOT_ENABLED", HttpStatus.BAD_REQUEST, "비밀번호 로그인을 사용할 수 없는 계정입니다."),
  UNSUPPORTED_TTS_SPEAKER(
      "USER_UNSUPPORTED_TTS_SPEAKER", HttpStatus.BAD_REQUEST, "지원하지 않는 TTS 목소리입니다.");

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
