package com.ssafy.backend.domain.translation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record SignToSpeechRequestDto(
    @Schema(description = "대화 세션 ID", example = "1") Long sessionId,
    @Schema(description = "온디바이스 수어 인식 결과 단어 배열", example = "[\"엄마\", \"해보다\"]")
        @NotEmpty(message = "단어 배열은 최소 1개 이상이어야 합니다.")
        List<@NotBlank(message = "단어는 비어 있을 수 없습니다.") String> words,
    @Schema(description = "문맥 보정 및 TTS 생성 기준 언어", example = "ko-KR", defaultValue = "ko-KR")
        @Pattern(regexp = "^ko-KR$", message = "지원하지 않는 locale입니다. 현재는 ko-KR만 지원합니다.")
        String locale,
    @Schema(description = "true면 TTS 생성 포함, false면 문장 보정만 수행", example = "true")
        Boolean requestTts) {

  public SignToSpeechRequestDto {
    if (locale == null) {
      locale = "ko-KR";
    }
  }
}
