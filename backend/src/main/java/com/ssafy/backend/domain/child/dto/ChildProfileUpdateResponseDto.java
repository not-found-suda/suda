package com.ssafy.backend.domain.child.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ChildProfileUpdateResponseDto(
    @Schema(description = "아이 프로필 ID", example = "1") Long childId,
    @Schema(description = "아이 이름", example = "민준이") String name,
    @Schema(description = "아이 생년월일", example = "2020-05-01") LocalDate birthDate,
    @Schema(description = "응답 시점 기준 만 나이", example = "6") int age,
    @Schema(description = "프로필 이미지 키", example = "yellow_circle") String avatarKey,
    @Schema(description = "활성 여부", example = "true") boolean active,
    @Schema(description = "수정 시각") LocalDateTime updatedAt) {}
