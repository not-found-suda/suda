package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponseDto(
    @Schema(description = "사용자 ID", example = "1") Long userId,
    @Schema(description = "사용자 이메일", example = "user1@test.com") String email,
    @Schema(description = "사용자 이름", example = "김보호") String name) {}
