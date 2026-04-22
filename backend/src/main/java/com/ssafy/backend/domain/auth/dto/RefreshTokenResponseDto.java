package com.ssafy.backend.domain.auth.dto;

public record RefreshTokenResponseDto(String accessToken, String tokenType, long expiresIn) {}
