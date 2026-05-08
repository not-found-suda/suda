package com.ssafy.backend.domain.social.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SocialAccountErrorCode implements ErrorCode {
  ALREADY_LINKED("SOCIAL_ACCOUNT_ALREADY_LINKED", HttpStatus.CONFLICT, "이미 다른 계정에 연동된 소셜 계정입니다.");

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
