package com.ssafy.backend.domain.child.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record ChildProfileUpdateRequestDto(
    @Schema(description = "아이 이름", example = "민준이") String name,
    @Schema(description = "아이 생년월일", example = "2020-05-01") LocalDate birthDate) {}
