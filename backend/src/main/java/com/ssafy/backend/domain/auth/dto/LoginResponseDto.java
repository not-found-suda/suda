package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponseDto(
    @Schema(description = "액세스 토큰", example = "<JWT>") String accessToken,
    @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
    @Schema(description = "만료 시간(초)", example = "900") long expiresIn) {}
