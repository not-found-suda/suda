package com.ssafy.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TtsSpeakerUpdateRequestDto(
    @NotBlank(message = "TTS speaker는 필수입니다.")
        @Schema(description = "선택할 CLOVA TTS speaker 코드", example = "vara")
        String ttsSpeaker) {}
