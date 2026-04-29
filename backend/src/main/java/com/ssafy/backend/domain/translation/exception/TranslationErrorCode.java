package com.ssafy.backend.domain.translation.exception;

import com.ssafy.backend.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TranslationErrorCode implements ErrorCode {
  INVALID_AUDIO(
      "TRANSLATION_INVALID_AUDIO", HttpStatus.BAD_REQUEST, "유효한 음성 파일이 필요합니다.", "유효하지 않은 음성 파일"),

  INVALID_LOCALE(
      "TRANSLATION_INVALID_LOCALE",
      HttpStatus.BAD_REQUEST,
      "지원하지 않는 locale입니다. 현재는 ko-KR만 지원합니다.",
      "지원하지 않는 언어 설정"),

  UNRECOGNIZABLE_AUDIO(
      "TRANSLATION_UNRECOGNIZABLE_AUDIO",
      HttpStatus.UNPROCESSABLE_ENTITY,
      "음성에서 유의미한 텍스트를 추출할 수 없습니다.",
      "음성 인식 결과 없음"),

  SIGN_CORRECTION_FAILED(
      "TRANSLATION_SIGN_CORRECTION_FAILED",
      HttpStatus.BAD_GATEWAY,
      "수어 문맥 보정 처리에 실패했습니다.",
      "Gemini 수어 문맥 보정 실패"),

  CHILD_SPEECH_CORRECTION_FAILED(
      "TRANSLATION_CHILD_SPEECH_CORRECTION_FAILED",
      HttpStatus.BAD_GATEWAY,
      "아이 발화 문맥 보정 처리에 실패했습니다.",
      "Gemini 아이 발화 보정 실패"),

  SPEECH_RECOGNITION_FAILED(
      "TRANSLATION_SPEECH_RECOGNITION_FAILED",
      HttpStatus.BAD_GATEWAY,
      "음성 인식 처리에 실패했습니다.",
      "Clova STT 처리 실패"),

  TEXT_TO_SPEECH_FAILED(
      "TRANSLATION_TEXT_TO_SPEECH_FAILED",
      HttpStatus.BAD_GATEWAY,
      "음성 합성 처리에 실패했습니다.",
      "Clova TTS 처리 실패");

  private final String code;
  private final HttpStatus httpStatus;
  private final String message;
  private final String title;

  TranslationErrorCode(String code, HttpStatus httpStatus, String message, String title) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
    this.title = title;
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
  public String getTitleOverride() {
    return title;
  }

  @Override
  public String getDomainTitle() {
    return "번역 처리 오류";
  }

  @Override
  public String getMessage() {
    return message;
  }
}
