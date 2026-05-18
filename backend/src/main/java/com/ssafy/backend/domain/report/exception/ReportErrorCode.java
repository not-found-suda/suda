package com.ssafy.backend.domain.report.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements ErrorCode {
  NOT_FOUND("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "퀴즈 기록을 찾을 수 없습니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  ReportErrorCode(String code, HttpStatus httpStatus, String message) {
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
    return "리포트 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
