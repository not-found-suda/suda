package com.ssafy.backend.domain.child.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ChildProfileResponseDto(
    @Schema(description = "아이 프로필 ID", example = "1") Long childId,
    @Schema(description = "아이 이름", example = "민준") String name,
    @Schema(description = "아이 생년월일", example = "2020-05-01") LocalDate birthDate,
    @Schema(description = "응답 시점 기준 만 나이", example = "6") int age,
    @Schema(description = "활성 여부", example = "true") boolean active,
    @Schema(description = "생성 시각") LocalDateTime createdAt,
    @Schema(description = "수정 시각") LocalDateTime updatedAt) {}
