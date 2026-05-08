package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.auth.exception.OAuthErrorCode;
import com.ssafy.backend.domain.social.entity.SocialAccount;
import com.ssafy.backend.domain.social.entity.SocialProvider;
import com.ssafy.backend.domain.social.repository.SocialAccountRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OAuthLoginProcessor {

  private static final int MAX_USER_NAME_LENGTH = 50;

  private final SocialAccountRepository socialAccountRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;

  public OAuthLoginProcessor(
      SocialAccountRepository socialAccountRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuthService authService) {
    this.socialAccountRepository = socialAccountRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authService = authService;
  }

  @Transactional
  public LoginResponseDto loginWithNaverProfile(NaverOAuthClient.NaverProfile profile) {
    User user = findOrCreateUser(profile);
    if (!user.isActive()) {
      throw new BusinessException(AuthErrorCode.INACTIVE_ACCOUNT);
    }

    return authService.issueLoginTokens(user.getId(), user.getRole()).response();
  }

  private User findOrCreateUser(NaverOAuthClient.NaverProfile profile) {
    return socialAccountRepository
        .findByProviderAndProviderUserId(SocialProvider.NAVER, profile.id())
        .map(SocialAccount::getUser)
        .orElseGet(() -> createUserAndSocialAccount(profile));
  }

  private User createUserAndSocialAccount(NaverOAuthClient.NaverProfile profile) {
    String email = normalizeEmail(profile.email());
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new BusinessException(OAuthErrorCode.EMAIL_ALREADY_EXISTS);
    }

    String name = resolveName(profile);
    String encodedPassword = passwordEncoder.encode("oauth:" + UUID.randomUUID());
    User user = userRepository.saveAndFlush(User.create(email, encodedPassword, name));
    socialAccountRepository.saveAndFlush(
        SocialAccount.create(user, SocialProvider.NAVER, profile.id(), email));
    return user;
  }

  private String resolveName(NaverOAuthClient.NaverProfile profile) {
    if (profile.name() != null && !profile.name().isBlank()) {
      return limitName(profile.name().trim());
    }
    if (profile.nickname() != null && !profile.nickname().isBlank()) {
      return limitName(profile.nickname().trim());
    }
    return limitName(normalizeEmail(profile.email()).split("@")[0]);
  }

  private String limitName(String name) {
    if (name.length() <= MAX_USER_NAME_LENGTH) {
      return name;
    }
    return name.substring(0, MAX_USER_NAME_LENGTH);
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
