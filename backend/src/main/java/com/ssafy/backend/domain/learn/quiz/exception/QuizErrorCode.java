package com.ssafy.backend.domain.learn.quiz.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum QuizErrorCode implements ErrorCode {
  SESSION_NOT_FOUND("QUIZ_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "퀴즈 세션을 찾을 수 없습니다."),
  SESSION_ALREADY_COMPLETED(
      "QUIZ_SESSION_ALREADY_COMPLETED", HttpStatus.BAD_REQUEST, "이미 종료된 퀴즈 세션입니다."),
  NOT_ENOUGH_WORDS("QUIZ_NOT_ENOUGH_WORDS", HttpStatus.BAD_REQUEST, "조건에 맞는 퀴즈 문제가 부족합니다."),
  NO_CURRENT_QUESTION("QUIZ_NO_CURRENT_QUESTION", HttpStatus.NOT_FOUND, "현재 풀 수 있는 문제가 없습니다."),
  QUESTION_NOT_FOUND("QUIZ_QUESTION_NOT_FOUND", HttpStatus.NOT_FOUND, "퀴즈 문제를 찾을 수 없습니다."),
  QUESTION_ALREADY_ANSWERED(
      "QUIZ_QUESTION_ALREADY_ANSWERED", HttpStatus.BAD_REQUEST, "이미 답변한 문제입니다."),
  QUESTION_NOT_IN_SESSION(
      "QUIZ_QUESTION_NOT_IN_SESSION", HttpStatus.BAD_REQUEST, "해당 세션의 문제가 아닙니다."),
  STT_FAILED("QUIZ_STT_FAILED", HttpStatus.BAD_GATEWAY, "음성 인식에 실패했습니다."),
  NOT_CURRENT_QUESTION("QUIZ_NOT_CURRENT_QUESTION", HttpStatus.BAD_REQUEST, "현재 풀 차례의 문제가 아닙니다.");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  QuizErrorCode(String code, HttpStatus httpStatus, String message) {
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
    return "퀴즈 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
