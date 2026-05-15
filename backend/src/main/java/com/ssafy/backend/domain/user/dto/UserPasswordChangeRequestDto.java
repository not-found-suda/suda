package com.ssafy.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserPasswordChangeRequestDto(
    @NotBlank(message = "현재 비밀번호는 필수입니다.") @Schema(description = "현재 비밀번호", example = "Password1!")
        String currentPassword,
    @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해야 합니다.")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]+$",
            message = "비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 각각 1자 이상 포함해야 합니다.")
        @Schema(description = "새 비밀번호", example = "NewPassword1!")
        String newPassword) {}
