package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequestDto(
    @NotBlank(message = "authorizationCode는 필수입니다.")
        @Schema(
            description = "네이버 authorization code",
            example = "naver-authorization-code",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationCode,
    @NotBlank(message = "state는 필수입니다.")
        @Schema(
            description = "네이버 로그인 요청에 사용한 state",
            example = "random-state",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String state,
    @NotBlank(message = "codeVerifier는 필수입니다.")
        @Schema(
            description = "PKCE code verifier",
            example = "pkce-code-verifier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String codeVerifier,
    @NotBlank(message = "redirectUri는 필수입니다.")
        @Schema(
            description = "네이버 authorize 요청에 사용한 redirect URI",
            example = "com.ssafy.mobile://oauth/naver",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String redirectUri) {}
