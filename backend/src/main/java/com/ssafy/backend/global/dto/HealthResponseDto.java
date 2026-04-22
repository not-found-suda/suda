package com.ssafy.backend.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HealthResponseDto(
    @Schema(description = "Service status", example = "UP") String status) {}
