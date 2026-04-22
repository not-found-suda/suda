package com.ssafy.backend.global.exception;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;

public enum ValidationErrorCode implements ErrorCode {
  REQUIRED_FIELD("VALIDATION_REQUIRED_FIELD", HttpStatus.BAD_REQUEST, "필수 값이 누락되었습니다."),
  INVALID_EMAIL_FORMAT(
      "VALIDATION_INVALID_EMAIL_FORMAT", HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다."),
  INVALID_LENGTH("VALIDATION_INVALID_LENGTH", HttpStatus.BAD_REQUEST, "길이가 올바르지 않습니다."),
  INVALID_PATTERN("VALIDATION_INVALID_PATTERN", HttpStatus.BAD_REQUEST, "형식이 올바르지 않습니다."),
  PASSWORD_LENGTH("VALIDATION_PASSWORD_LENGTH", HttpStatus.BAD_REQUEST, "비밀번호 길이가 올바르지 않습니다."),
  PASSWORD_CHARSET("VALIDATION_PASSWORD_CHARSET", HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
  INVALID_INPUT("VALIDATION_INVALID_INPUT", HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  ValidationErrorCode(String code, HttpStatus httpStatus, String message) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public static ValidationErrorCode from(FieldError fieldError) {
    if (fieldError == null) {
      return INVALID_INPUT;
    }
    return from(fieldError.getCode(), fieldError.getField());
  }

  public static ValidationErrorCode from(String validationType, String fieldName) {
    if ("NotBlank".equals(validationType) || "NotNull".equals(validationType)) {
      return REQUIRED_FIELD;
    }
    if ("Email".equals(validationType)) {
      return INVALID_EMAIL_FORMAT;
    }
    if ("Size".equals(validationType)) {
      return isPasswordField(fieldName) ? PASSWORD_LENGTH : INVALID_LENGTH;
    }
    if ("Pattern".equals(validationType)) {
      return isPasswordField(fieldName) ? PASSWORD_CHARSET : INVALID_PATTERN;
    }

    return INVALID_INPUT;
  }

  private static boolean isPasswordField(String fieldName) {
    if (fieldName == null) {
      return false;
    }

    String normalized = fieldName.toLowerCase(Locale.ROOT);
    return "password".equals(normalized) || normalized.endsWith(".password");
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
