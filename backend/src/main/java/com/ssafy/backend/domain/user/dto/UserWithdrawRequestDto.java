package com.ssafy.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserWithdrawRequestDto(
    @Schema(description = "현재 비밀번호. 비밀번호 로그인 계정인 경우 필수", example = "Password1!") String password) {}
