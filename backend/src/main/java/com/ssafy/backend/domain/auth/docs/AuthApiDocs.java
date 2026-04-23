package com.ssafy.backend.domain.auth.docs;

import com.ssafy.backend.domain.auth.dto.LoginRequestDto;
import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.dto.RefreshTokenResponseDto;
import com.ssafy.backend.domain.auth.dto.SignupRequestDto;
import com.ssafy.backend.domain.auth.dto.SignupResponseDto;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증 API", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 API")
public interface AuthApiDocs {

  @Operation(summary = "회원가입", description = "새 사용자 계정을 생성합니다.")
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "AUTH_DUPLICATE_EMAIL"})
  @ApiResponse(
      responseCode = "201",
      description = "생성됨",
      content = @Content(schema = @Schema(implementation = SignupResponseDto.class)))
  ResponseEntity<SignupResponseDto> signup(SignupRequestDto request);

  @Operation(summary = "로그인", description = "액세스/리프레시 토큰을 발급합니다.")
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "AUTH_INVALID_CREDENTIALS", "AUTH_INACTIVE_ACCOUNT"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = LoginResponseDto.class)))
  ResponseEntity<LoginResponseDto> login(LoginRequestDto request);

  @Operation(summary = "토큰 재발급", description = "리프레시 토큰 쿠키를 사용해 액세스 토큰을 재발급합니다.")
  @ApiErrorCodes({"AUTH_INVALID_REFRESH_TOKEN", "AUTH_INACTIVE_ACCOUNT"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = RefreshTokenResponseDto.class)))
  ResponseEntity<RefreshTokenResponseDto> refresh(HttpServletRequest request);

  @Operation(
      summary = "로그아웃",
      description = "리프레시 토큰을 무효화하고 현재 액세스 토큰을 블랙리스트 처리합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  @ApiResponse(responseCode = "204", description = "콘텐츠 없음")
  ResponseEntity<Void> logout(HttpServletRequest request);
}
