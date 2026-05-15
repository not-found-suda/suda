package com.ssafy.backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequestDto(
    @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
    @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해야 합니다.")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]+$",
            message = "비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 각각 1자 이상 포함해야 합니다.")
        String password,
    @NotBlank(message = "이름은 필수입니다.") @Size(max = 50, message = "이름은 50자 이하로 입력해야 합니다.")
        String name) {}
