package com.ssafy.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequestDto(
    @NotBlank(message = "재설정 토큰은 필수입니다.")
        @Schema(description = "인증 코드 검증 후 발급된 재설정 토큰", example = "password-reset-token")
        String resetToken,
    @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해야 합니다.")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]+$",
            message = "비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 각각 1자 이상 포함해야 합니다.")
        @Schema(description = "새 비밀번호", example = "NewPassword1!")
        String newPassword) {}
