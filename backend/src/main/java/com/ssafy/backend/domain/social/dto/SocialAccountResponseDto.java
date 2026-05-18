package com.ssafy.backend.domain.social.dto;

import com.ssafy.backend.domain.social.entity.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record SocialAccountResponseDto(
    @Schema(description = "소셜 제공자", example = "NAVER") SocialProvider provider,
    @Schema(description = "연동 여부", example = "true") boolean linked,
    @Schema(description = "소셜 제공자 이메일", example = "user@naver.com") String providerEmail,
    @Schema(description = "연동 시각") LocalDateTime linkedAt) {}
