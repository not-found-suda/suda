package com.ssafy.backend.domain.auth.controller;

import com.ssafy.backend.domain.auth.docs.AuthApiDocs;
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
import com.ssafy.backend.domain.auth.service.AuthService;
import com.ssafy.backend.domain.auth.service.OAuthService;
import com.ssafy.backend.domain.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiDocs {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService authService;
  private final OAuthService oAuthService;
  private final PasswordResetService passwordResetService;

  public AuthController(
      AuthService authService,
      OAuthService oAuthService,
      PasswordResetService passwordResetService) {
    this.authService = authService;
    this.oAuthService = oAuthService;
    this.passwordResetService = passwordResetService;
  }

  @PostMapping("/signup")
  @Override
  public ResponseEntity<SignupResponseDto> signup(@Valid @RequestBody SignupRequestDto requestDto) {
    SignupResponseDto responseDto = authService.signup(requestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
  }

  @PostMapping("/login")
  @Override
  public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
    AuthService.LoginResult loginResult = authService.login(requestDto);
    return ResponseEntity.ok(loginResult.response());
  }

  @PostMapping("/refresh")
  @Override
  public ResponseEntity<RefreshTokenResponseDto> refresh(
      @RequestBody RefreshTokenRequestDto requestDto) {
    AuthService.RefreshTokenResult refreshResult = authService.refresh(requestDto.refreshToken());
    return ResponseEntity.ok(refreshResult.response());
  }

  @PostMapping("/logout")
  @Override
  public ResponseEntity<Void> logout(
      @RequestBody RefreshTokenRequestDto requestDto, HttpServletRequest request) {
    String accessToken = resolveAccessToken(request);
    authService.logout(requestDto.refreshToken(), accessToken);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/oauth/naver")
  @Override
  public ResponseEntity<LoginResponseDto> loginWithNaver(
      @Valid @RequestBody OAuthLoginRequestDto requestDto) {
    return ResponseEntity.ok(oAuthService.loginWithNaver(requestDto));
  }

  @PostMapping("/password-reset/request")
  @Override
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequestDto requestDto) {
    passwordResetService.request(requestDto.email());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password-reset/verify")
  @Override
  public ResponseEntity<PasswordResetVerifyResponseDto> verifyPasswordReset(
      @Valid @RequestBody PasswordResetVerifyRequestDto requestDto) {
    return ResponseEntity.ok(passwordResetService.verify(requestDto.email(), requestDto.code()));
  }

  @PostMapping("/password-reset/confirm")
  @Override
  public ResponseEntity<Void> confirmPasswordReset(
      @Valid @RequestBody PasswordResetConfirmRequestDto requestDto) {
    passwordResetService.confirm(requestDto.resetToken(), requestDto.newPassword());
    return ResponseEntity.noContent().build();
  }

  private String resolveAccessToken(HttpServletRequest request) {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return authorizationHeader.substring(BEARER_PREFIX.length());
  }
}
