package com.ssafy.backend.domain.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialAccountLinkRequestDto(
    @NotBlank(message = "providerAccessToken은 필수입니다.")
        @Schema(
            description = "네이버 Android SDK가 발급한 provider Access Token",
            example = "naver-access-token")
        String providerAccessToken) {}
