package com.ssafy.backend.domain.user.dto;

import com.ssafy.backend.global.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record UserUpdateResponseDto(
    @Schema(description = "사용자 ID", example = "1") Long userId,
    @Schema(description = "이메일", example = "user1@test.com") String email,
    @Schema(description = "사용자 이름", example = "김보호") String name,
    @Schema(description = "활성 여부", example = "true") boolean active,
    @Schema(description = "권한", example = "USER") Role role,
    @Schema(description = "수정 시각", example = "2026-05-07T10:00:00") LocalDateTime updatedAt) {}
