package com.ssafy.backend.domain.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SocialAccountListResponseDto(
    @Schema(description = "소셜 계정 연동 상태 목록") List<SocialAccountResponseDto> accounts) {}
