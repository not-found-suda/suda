package com.ssafy.backend.domain.child.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ChildProfileListResponseDto(
    @Schema(description = "아이 프로필 목록") List<ChildProfileSummaryResponseDto> children) {}
