package com.ssafy.backend.domain.comms.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CommunicationSessionErrorCode implements ErrorCode {
  SESSION_NOT_FOUND("COMMUNICATION_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "대화 세션을 찾을 수 없습니다."),

  SESSION_ALREADY_ENDED(
      "COMMUNICATION_SESSION_ALREADY_ENDED", HttpStatus.CONFLICT, "이미 종료된 대화 세션입니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  CommunicationSessionErrorCode(String code, HttpStatus httpStatus, String message) {
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
    return "대화 세션 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
