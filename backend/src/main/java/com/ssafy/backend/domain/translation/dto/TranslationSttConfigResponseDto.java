package com.ssafy.backend.domain.translation.dto;

import com.ssafy.backend.domain.translation.config.TranslationSttMode;
import io.swagger.v3.oas.annotations.media.Schema;

public record TranslationSttConfigResponseDto(
    @Schema(description = "translation STT 처리 모드", example = "REST") TranslationSttMode mode) {}
