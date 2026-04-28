package com.ssafy.backend.domain.translation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpeechToTextResponseDto(
    @Schema(description = "Clova STT가 1차 추출한 원문 텍스트", example = "옴마") String recognizedText,
    @Schema(description = "Gemini가 발음 및 문맥 보정한 최종 텍스트", example = "엄마!") String correctedText,
    @Schema(description = "STT 원문 대비 보정 여부", example = "true") boolean corrected,
    @Schema(description = "STT 신뢰도 값", example = "0.91") Double confidence,
    @Schema(description = "실제 처리에 사용된 언어 정보", example = "ko-KR") String locale) {}
