package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthStatusResponseDto(
    @Schema(description = "인증 상태 메시지", example = "인증되었습니다.") String message) {}
