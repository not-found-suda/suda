package com.ssafy.backend.domain.auth.docs;

import com.ssafy.backend.domain.auth.dto.LoginRequestDto;
import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.dto.RefreshTokenResponseDto;
import com.ssafy.backend.domain.auth.dto.SignupRequestDto;
import com.ssafy.backend.domain.auth.dto.SignupResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증 API", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 API")
public interface AuthApiDocs {

  @Operation(summary = "회원가입", description = "새 사용자 계정을 생성합니다.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "생성됨",
            content =
                @Content(
                    schema = @Schema(implementation = SignupResponseDto.class),
                    examples = @ExampleObject(value = AuthSwaggerExamples.SIGNUP_SUCCESS))),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "중복 이메일",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  ResponseEntity<SignupResponseDto> signup(SignupRequestDto request);

  @Operation(summary = "로그인", description = "액세스/리프레시 토큰을 발급합니다.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content =
                @Content(
                    schema = @Schema(implementation = LoginResponseDto.class),
                    examples = @ExampleObject(value = AuthSwaggerExamples.LOGIN_SUCCESS))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  ResponseEntity<LoginResponseDto> login(LoginRequestDto request);

  @Operation(summary = "토큰 재발급", description = "리프레시 토큰 쿠키를 사용해 액세스 토큰을 재발급합니다.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content =
                @Content(
                    schema = @Schema(implementation = RefreshTokenResponseDto.class),
                    examples = @ExampleObject(value = AuthSwaggerExamples.REFRESH_SUCCESS))),
        @ApiResponse(
            responseCode = "401",
            description = "유효하지 않은 리프레시 토큰",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = AuthSwaggerExamples.INVALID_REFRESH_TOKEN)))
      })
  ResponseEntity<RefreshTokenResponseDto> refresh(HttpServletRequest request);

  @Operation(
      summary = "로그아웃",
      description = "리프레시 토큰을 무효화하고 현재 액세스 토큰을 블랙리스트 처리합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "콘텐츠 없음"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  ResponseEntity<Void> logout(HttpServletRequest request);
}
