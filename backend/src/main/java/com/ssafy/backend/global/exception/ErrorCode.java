package com.ssafy.backend.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  String getCode();

  HttpStatus getHttpStatus();

  default String getTitleOverride() {
    return null;
  }

  default String getDomainTitle() {
    return null;
  }

  default String getTitle() {
    return getTitleOverride();
  }

  String getMessage();
}
