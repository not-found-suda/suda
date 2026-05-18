package com.ssafy.backend.domain.user.dto;

import com.ssafy.backend.global.security.Role;

public record UserAuthResponseDto(
    Long id,
    String email,
    String password,
    boolean passwordLoginEnabled,
    boolean active,
    Role role) {}
