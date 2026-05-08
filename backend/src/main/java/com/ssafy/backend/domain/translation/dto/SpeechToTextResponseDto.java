package com.ssafy.backend.domain.translation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpeechToTextResponseDto(
    @Schema(description = "Clova STT가 추출한 원문 텍스트", example = "원숭이") String recognizedText,
    @Schema(description = "최종 표시 텍스트. 현재는 STT 결과를 그대로 반환합니다.", example = "원숭이")
        String correctedText,
    @Schema(description = "STT 원문 대비 보정 여부. 현재는 보정하지 않으므로 false입니다.", example = "false")
        boolean corrected,
    @Schema(description = "STT 신뢰도 값. 현재는 별도 신뢰도 값을 반환하지 않으므로 null입니다.", example = "null")
        Double confidence,
    @Schema(description = "실제 처리에 사용된 언어 정보", example = "ko-KR") String locale) {}
