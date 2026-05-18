package com.ssafy.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequestDto(
    @NotBlank(message = "이름은 필수입니다.") @Size(max = 50, message = "이름은 50자 이하로 입력해야 합니다.")
        String name) {}
