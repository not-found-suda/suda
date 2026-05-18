package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordResetVerifyResponseDto(
    @Schema(description = "새 비밀번호 설정에 사용할 재설정 토큰", example = "password-reset-token")
        String resetToken,
    @Schema(description = "재설정 토큰 만료까지 남은 시간(초)", example = "600") long expiresInSeconds) {}
