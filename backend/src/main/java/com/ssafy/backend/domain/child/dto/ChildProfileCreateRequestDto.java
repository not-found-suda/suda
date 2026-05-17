package com.ssafy.backend.domain.child.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record ChildProfileCreateRequestDto(
    @Schema(description = "아이 이름", example = "민준", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "아이 생년월일",
            example = "2020-05-01",
            requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate birthDate,
    @Schema(description = "프로필 이미지 키", example = "purple_diamond") String avatarKey) {}
