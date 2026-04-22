package com.ssafy.backend.domain.auth.dto;

public record LoginResponseDto(String accessToken, String tokenType, long expiresIn) {}
