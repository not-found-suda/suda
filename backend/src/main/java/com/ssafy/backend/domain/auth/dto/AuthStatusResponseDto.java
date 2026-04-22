package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthStatusResponseDto(
    @Schema(description = "Authentication status message", example = "authenticated")
        String message) {}
