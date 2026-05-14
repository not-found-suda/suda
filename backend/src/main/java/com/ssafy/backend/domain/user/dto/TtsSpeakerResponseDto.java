package com.ssafy.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TtsSpeakerResponseDto(
    @Schema(description = "CLOVA TTS speaker 코드", example = "vara") String code,
    @Schema(description = "앱에 표시할 목소리 이름", example = "따뜻한 엄마 목소리") String label) {}
