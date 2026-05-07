package com.ssafy.backend.domain.child.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChildProfileErrorCode implements ErrorCode {
  INVALID_NAME(
      "CHILD_PROFILE_INVALID_NAME", HttpStatus.BAD_REQUEST, "아이 이름은 1자 이상 20자 이하로 입력해야 합니다."),
  INVALID_BIRTH_DATE(
      "CHILD_PROFILE_INVALID_BIRTH_DATE", HttpStatus.BAD_REQUEST, "생년월일은 0세 이상 18세 이하 범위여야 합니다."),
  NOT_FOUND("CHILD_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "아이 프로필을 찾을 수 없습니다."),
  DUPLICATE_NAME("CHILD_PROFILE_DUPLICATE_NAME", HttpStatus.CONFLICT, "이미 등록된 아이 이름입니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  ChildProfileErrorCode(String code, HttpStatus httpStatus, String message) {
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
    return "아이 프로필 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
