package com.ssafy.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TtsSpeakerUpdateResponseDto(
    @Schema(description = "사용자 ID", example = "1") Long userId,
    @Schema(description = "선택된 TTS speaker 코드", example = "vara") String ttsSpeaker) {}
