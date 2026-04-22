package com.ssafy.backend.domain.user.dto;

import com.ssafy.backend.global.security.Role;

public record UserResponseDto(Long userId, String email, boolean active, Role role) {}
