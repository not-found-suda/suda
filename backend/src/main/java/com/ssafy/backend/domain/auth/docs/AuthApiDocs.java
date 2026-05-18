package com.ssafy.backend.domain.auth.docs;

import com.ssafy.backend.domain.auth.dto.LoginRequestDto;
import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.dto.OAuthLoginRequestDto;
import com.ssafy.backend.domain.auth.dto.PasswordResetConfirmRequestDto;
import com.ssafy.backend.domain.auth.dto.PasswordResetRequestDto;
import com.ssafy.backend.domain.auth.dto.PasswordResetVerifyRequestDto;
import com.ssafy.backend.domain.auth.dto.PasswordResetVerifyResponseDto;
import com.ssafy.backend.domain.auth.dto.RefreshTokenRequestDto;
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

  @Operation(summary = "토큰 재발급", description = "Request Body의 리프레시 토큰으로 액세스 토큰을 재발급합니다.")
  @ApiErrorCodes({"AUTH_INVALID_REFRESH_TOKEN", "AUTH_INACTIVE_ACCOUNT"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = RefreshTokenResponseDto.class)))
  ResponseEntity<RefreshTokenResponseDto> refresh(RefreshTokenRequestDto request);

  @Operation(
      summary = "로그아웃",
      description = "리프레시 토큰을 무효화하고 현재 액세스 토큰을 블랙리스트 처리합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  @ApiResponse(responseCode = "204", description = "콘텐츠 없음")
  ResponseEntity<Void> logout(RefreshTokenRequestDto requestDto, HttpServletRequest request);

  @Operation(
      summary = "네이버 OAuth 로그인",
      description = "네이버 Android SDK가 발급한 provider Access Token으로 소셜 로그인을 처리합니다.")
  @ApiErrorCodes({
    "VALIDATION_INVALID_INPUT",
    "OAUTH_INVALID_PROVIDER_TOKEN",
    "OAUTH_EMAIL_ALREADY_EXISTS",
    "AUTH_INACTIVE_ACCOUNT",
    "SOCIAL_ACCOUNT_ALREADY_LINKED",
    "OAUTH_PROVIDER_ERROR"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = LoginResponseDto.class)))
  ResponseEntity<LoginResponseDto> loginWithNaver(OAuthLoginRequestDto request);

  @Operation(
      summary = "비밀번호 재설정 인증 코드 요청",
      description = "이메일로 비밀번호 재설정 인증 코드를 요청합니다. 가입 여부 노출을 막기 위해 동일한 성공 응답을 반환합니다.")
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT"})
  @ApiResponse(responseCode = "204", description = "콘텐츠 없음")
  ResponseEntity<Void> requestPasswordReset(PasswordResetRequestDto request);

  @Operation(summary = "비밀번호 재설정 인증 코드 검증", description = "이메일과 인증 코드를 검증하고 새 비밀번호 설정용 토큰을 발급합니다.")
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "AUTH_INVALID_PASSWORD_RESET_CODE"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = PasswordResetVerifyResponseDto.class)))
  ResponseEntity<PasswordResetVerifyResponseDto> verifyPasswordReset(
      PasswordResetVerifyRequestDto request);

  @Operation(summary = "새 비밀번호 재설정 확정", description = "재설정 토큰으로 새 비밀번호를 설정합니다.")
  @ApiErrorCodes({
    "VALIDATION_INVALID_INPUT",
    "AUTH_INVALID_PASSWORD_RESET_TOKEN",
    "USER_NEW_PASSWORD_SAME_AS_CURRENT",
    "USER_PASSWORD_LOGIN_NOT_ENABLED"
  })
  @ApiResponse(responseCode = "204", description = "콘텐츠 없음")
  ResponseEntity<Void> confirmPasswordReset(PasswordResetConfirmRequestDto request);
}
