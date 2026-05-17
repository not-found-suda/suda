package com.ssafy.backend.domain.user.docs;

import com.ssafy.backend.domain.social.dto.SocialAccountLinkRequestDto;
import com.ssafy.backend.domain.social.dto.SocialAccountListResponseDto;
import com.ssafy.backend.domain.social.dto.SocialAccountResponseDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerListResponseDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerUpdateRequestDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerUpdateResponseDto;
import com.ssafy.backend.domain.user.dto.UserPasswordChangeRequestDto;
import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.domain.user.dto.UserUpdateRequestDto;
import com.ssafy.backend.domain.user.dto.UserUpdateResponseDto;
import com.ssafy.backend.domain.user.dto.UserWithdrawRequestDto;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "사용자 API", description = "사용자 관련 API")
public interface UserApiDocs {

  @Operation(
      summary = "내 정보 조회",
      description = "인증된 사용자의 정보를 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "USER_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = UserResponseDto.class)))
  ResponseEntity<UserResponseDto> me(@Parameter(hidden = true) Authentication authentication);

  @Operation(
      summary = "내 정보 수정",
      description = "인증된 사용자의 정보를 수정합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"VALIDATION_INVALID_INPUT", "COMMON_UNAUTHORIZED", "USER_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = UserUpdateResponseDto.class)))
  ResponseEntity<UserUpdateResponseDto> updateMe(
      @Parameter(hidden = true) Authentication authentication,
      @Valid @RequestBody UserUpdateRequestDto request);

  @Operation(
      summary = "내 비밀번호 변경",
      description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "VALIDATION_REQUIRED_FIELD",
    "VALIDATION_PASSWORD_LENGTH",
    "VALIDATION_PASSWORD_CHARSET",
    "COMMON_UNAUTHORIZED",
    "USER_NOT_FOUND",
    "USER_PASSWORD_LOGIN_NOT_ENABLED",
    "USER_CURRENT_PASSWORD_MISMATCH",
    "USER_NEW_PASSWORD_SAME_AS_CURRENT"
  })
  @ApiResponse(responseCode = "204", description = "성공")
  ResponseEntity<Void> changePassword(
      @Parameter(hidden = true) Authentication authentication,
      @Valid @RequestBody UserPasswordChangeRequestDto request);

  @Operation(
      summary = "회원 탈퇴",
      description =
          "인증된 사용자의 계정을 비활성화합니다. 비밀번호 로그인 계정은 현재 비밀번호 확인이 필요하며, 소셜 전용 계정은 비밀번호 없이 탈퇴할 수 있습니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "COMMON_UNAUTHORIZED",
    "USER_NOT_FOUND",
    "USER_WITHDRAW_PASSWORD_REQUIRED",
    "USER_CURRENT_PASSWORD_MISMATCH"
  })
  @ApiResponse(responseCode = "204", description = "성공")
  ResponseEntity<Void> withdrawMe(
      @Parameter(hidden = true) Authentication authentication,
      @RequestBody(required = false) UserWithdrawRequestDto request);

  @Operation(
      summary = "TTS 목소리 후보 목록 조회",
      description = "부모가 선택할 수 있는 TTS 목소리 후보 목록을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = TtsSpeakerListResponseDto.class)))
  ResponseEntity<TtsSpeakerListResponseDto> getTtsSpeakers(
      @Parameter(hidden = true) Authentication authentication);

  @Operation(
      summary = "내 TTS 목소리 설정 변경",
      description = "인증된 사용자의 TTS 목소리 설정을 변경합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "VALIDATION_INVALID_INPUT",
    "COMMON_UNAUTHORIZED",
    "USER_NOT_FOUND",
    "USER_UNSUPPORTED_TTS_SPEAKER"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = TtsSpeakerUpdateResponseDto.class)))
  ResponseEntity<TtsSpeakerUpdateResponseDto> updateTtsSpeaker(
      @Parameter(hidden = true) Authentication authentication,
      @Valid @RequestBody TtsSpeakerUpdateRequestDto request);

  @Operation(
      summary = "내 소셜 계정 연동 상태 조회",
      description = "인증된 사용자의 소셜 계정 연동 상태를 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "USER_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = SocialAccountListResponseDto.class)))
  ResponseEntity<SocialAccountListResponseDto> getSocialAccounts(
      @Parameter(hidden = true) Authentication authentication);

  @Operation(
      summary = "네이버 계정 연동",
      description = "네이버 Android SDK가 발급한 provider Access Token으로 현재 계정에 네이버 계정을 연동합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "VALIDATION_REQUIRED_FIELD",
    "COMMON_UNAUTHORIZED",
    "USER_NOT_FOUND",
    "OAUTH_INVALID_PROVIDER_TOKEN",
    "OAUTH_PROVIDER_ERROR",
    "SOCIAL_ACCOUNT_ALREADY_LINKED",
    "SOCIAL_ACCOUNT_LINKED_TO_OTHER_USER",
    "SOCIAL_ACCOUNT_EMAIL_MISMATCH"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = SocialAccountResponseDto.class)))
  ResponseEntity<SocialAccountResponseDto> linkNaver(
      @Parameter(hidden = true) Authentication authentication,
      @Valid @RequestBody SocialAccountLinkRequestDto request);

  @Operation(
      summary = "네이버 계정 연동 해제",
      description = "현재 계정에 연동된 네이버 계정을 해제합니다. 마지막 로그인 수단이면 해제할 수 없습니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "COMMON_UNAUTHORIZED",
    "USER_NOT_FOUND",
    "SOCIAL_ACCOUNT_NOT_LINKED",
    "SOCIAL_ACCOUNT_LAST_LOGIN_METHOD"
  })
  @ApiResponse(responseCode = "204", description = "성공")
  ResponseEntity<Void> unlinkNaver(@Parameter(hidden = true) Authentication authentication);
}
