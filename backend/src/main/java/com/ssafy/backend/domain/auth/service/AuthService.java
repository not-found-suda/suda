package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.domain.auth.dto.LoginRequestDto;
import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.dto.RefreshTokenResponseDto;
import com.ssafy.backend.domain.auth.dto.SignupRequestDto;
import com.ssafy.backend.domain.auth.dto.SignupResponseDto;
import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.user.dto.UserAuthResponseDto;
import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.service.UserService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.security.Role;
import com.ssafy.backend.global.security.jwt.JwtProperties;
import com.ssafy.backend.global.security.jwt.JwtTokenProvider;
import java.time.Duration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;
  private final RefreshTokenStore refreshTokenStore;
  private final AccessTokenBlacklistStore accessTokenBlacklistStore;

  public AuthService(
      UserService userService,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      JwtProperties jwtProperties,
      RefreshTokenStore refreshTokenStore,
      AccessTokenBlacklistStore accessTokenBlacklistStore) {
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.jwtProperties = jwtProperties;
    this.refreshTokenStore = refreshTokenStore;
    this.accessTokenBlacklistStore = accessTokenBlacklistStore;
  }

  @Transactional
  public SignupResponseDto signup(SignupRequestDto requestDto) {
    if (userService.existsByEmail(requestDto.email())) {
      throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
    }

    UserResponseDto user =
        userService.register(requestDto.email(), requestDto.password(), requestDto.name());
    return new SignupResponseDto(user.userId(), user.email(), user.name());
  }

  @Transactional
  public LoginResult login(LoginRequestDto requestDto) {
    UserAuthResponseDto user = getUserForLogin(requestDto.email());
    if (!user.active()) {
      throw new BusinessException(AuthErrorCode.INACTIVE_ACCOUNT);
    }

    if (!user.passwordLoginEnabled() || !isValidPassword(requestDto.password(), user.password())) {
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    TokenPair tokenPair = createTokenPair(user.id(), user.role());
    return toLoginResult(tokenPair);
  }

  @Transactional
  public RefreshTokenResult refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (!jwtTokenProvider.validateToken(refreshToken)
        || !jwtTokenProvider.isRefreshToken(refreshToken)) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    String oldJti = jwtTokenProvider.getJti(refreshToken);
    if (oldJti == null || oldJti.isBlank()) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    Long userId = jwtTokenProvider.getUserId(refreshToken);
    Long storedUserId = refreshTokenStore.findUserIdByJti(oldJti);
    if (storedUserId == null || !storedUserId.equals(userId)) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    refreshTokenStore.delete(oldJti);
    UserAuthResponseDto user = getUserForRefresh(userId);
    if (!user.active()) {
      throw new BusinessException(AuthErrorCode.INACTIVE_ACCOUNT);
    }
    TokenPair tokenPair = createTokenPair(user.id(), user.role());
    persistRefreshToken(tokenPair.refreshToken());

    RefreshTokenResponseDto response =
        new RefreshTokenResponseDto(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            "Bearer",
            jwtProperties.getAccessTokenTtlSeconds());
    return new RefreshTokenResult(response, tokenPair.refreshToken());
  }

  @Transactional
  public void logout(String refreshToken, String accessToken) {
    if (refreshToken != null
        && !refreshToken.isBlank()
        && jwtTokenProvider.validateToken(refreshToken)
        && jwtTokenProvider.isRefreshToken(refreshToken)) {
      String refreshJti = jwtTokenProvider.getJti(refreshToken);
      if (refreshJti != null && !refreshJti.isBlank()) {
        refreshTokenStore.delete(refreshJti);
      }
    }

    if (accessToken == null || accessToken.isBlank()) {
      return;
    }
    if (!jwtTokenProvider.validateToken(accessToken)
        || !jwtTokenProvider.isAccessToken(accessToken)) {
      return;
    }

    String accessJti = jwtTokenProvider.getJti(accessToken);
    if (accessJti == null || accessJti.isBlank()) {
      return;
    }

    long ttlSeconds = jwtTokenProvider.getRemainingValiditySeconds(accessToken);
    if (ttlSeconds <= 0) {
      return;
    }
    accessTokenBlacklistStore.blacklist(accessJti, Duration.ofSeconds(ttlSeconds));
  }

  @Transactional
  public LoginResult issueLoginTokens(Long userId, Role role) {
    TokenPair tokenPair = createTokenPair(userId, role);
    return toLoginResult(tokenPair);
  }

  private boolean isValidPassword(String rawPassword, String storedPassword) {
    if (rawPassword == null || storedPassword == null) {
      return false;
    }
    return passwordEncoder.matches(rawPassword, storedPassword);
  }

  private UserAuthResponseDto getUserForLogin(String email) {
    try {
      return userService.getAuthInfoByEmail(email);
    } catch (BusinessException exception) {
      if (exception.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
        throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
      }
      throw exception;
    }
  }

  private UserAuthResponseDto getUserForRefresh(Long userId) {
    try {
      return userService.getAuthInfoById(userId);
    } catch (BusinessException exception) {
      if (exception.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
        throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
      }
      throw exception;
    }
  }

  private TokenPair createTokenPair(Long userId, Role role) {
    String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
    String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
    return new TokenPair(accessToken, refreshToken);
  }

  private void persistRefreshToken(String refreshToken) {
    String refreshJti = jwtTokenProvider.getJti(refreshToken);
    Long userId = jwtTokenProvider.getUserId(refreshToken);
    refreshTokenStore.save(
        refreshJti, userId, Duration.ofSeconds(jwtProperties.getRefreshTokenTtlSeconds()));
  }

  private LoginResult toLoginResult(TokenPair tokenPair) {
    persistRefreshToken(tokenPair.refreshToken());
    LoginResponseDto response =
        new LoginResponseDto(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            "Bearer",
            jwtProperties.getAccessTokenTtlSeconds());
    return new LoginResult(response, tokenPair.refreshToken());
  }

  public record LoginResult(LoginResponseDto response, String refreshToken) {}

  public record RefreshTokenResult(RefreshTokenResponseDto response, String refreshToken) {}

  private record TokenPair(String accessToken, String refreshToken) {}
}
