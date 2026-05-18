package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshTokenRequestDto(
    @Schema(description = "리프레시 토큰", example = "<JWT>", requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken) {}
