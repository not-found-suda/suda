package com.ssafy.backend.domain.social.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SocialAccountErrorCode implements ErrorCode {
  ALREADY_LINKED("SOCIAL_ACCOUNT_ALREADY_LINKED", HttpStatus.CONFLICT, "이미 연동된 소셜 계정입니다."),
  LINKED_TO_OTHER_USER(
      "SOCIAL_ACCOUNT_LINKED_TO_OTHER_USER", HttpStatus.CONFLICT, "이미 다른 계정에 연동된 소셜 계정입니다."),
  EMAIL_MISMATCH(
      "SOCIAL_ACCOUNT_EMAIL_MISMATCH", HttpStatus.CONFLICT, "현재 계정 이메일과 소셜 계정 이메일이 일치하지 않습니다."),
  NOT_LINKED("SOCIAL_ACCOUNT_NOT_LINKED", HttpStatus.NOT_FOUND, "연동된 소셜 계정이 없습니다."),
  LAST_LOGIN_METHOD(
      "SOCIAL_ACCOUNT_LAST_LOGIN_METHOD", HttpStatus.CONFLICT, "마지막 로그인 수단은 해제할 수 없습니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  SocialAccountErrorCode(String code, HttpStatus httpStatus, String message) {
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
    return "소셜 계정 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
