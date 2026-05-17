package com.ssafy.backend.domain.social.service;

import com.ssafy.backend.domain.auth.service.NaverOAuthClient;
import com.ssafy.backend.domain.social.dto.SocialAccountListResponseDto;
import com.ssafy.backend.domain.social.dto.SocialAccountResponseDto;
import com.ssafy.backend.domain.social.entity.SocialAccount;
import com.ssafy.backend.domain.social.entity.SocialProvider;
import com.ssafy.backend.domain.social.exception.SocialAccountErrorCode;
import com.ssafy.backend.domain.social.repository.SocialAccountRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SocialAccountService {

  private final SocialAccountRepository socialAccountRepository;
  private final UserRepository userRepository;
  private final NaverOAuthClient naverOAuthClient;

  public SocialAccountService(
      SocialAccountRepository socialAccountRepository,
      UserRepository userRepository,
      NaverOAuthClient naverOAuthClient) {
    this.socialAccountRepository = socialAccountRepository;
    this.userRepository = userRepository;
    this.naverOAuthClient = naverOAuthClient;
  }

  @Transactional(readOnly = true)
  public SocialAccountListResponseDto getMySocialAccounts(Long userId) {
    getActiveUser(userId);
    Map<SocialProvider, SocialAccount> linkedAccounts =
        socialAccountRepository.findByUserId(userId).stream()
            .collect(Collectors.toMap(SocialAccount::getProvider, Function.identity()));

    return new SocialAccountListResponseDto(
        Arrays.stream(SocialProvider.values())
            .map(provider -> toResponse(provider, linkedAccounts.get(provider)))
            .toList());
  }

  public SocialAccountResponseDto linkNaver(Long userId, String providerAccessToken) {
    User user = getActiveUser(userId);
    NaverOAuthClient.NaverProfile profile = naverOAuthClient.getProfile(providerAccessToken);
    String providerEmail = normalizeEmail(profile.email());
    validateProviderEmail(user, providerEmail);

    return socialAccountRepository
        .findByUserIdAndProvider(userId, SocialProvider.NAVER)
        .map(existing -> validateAndReturnExisting(existing, profile.id()))
        .orElseGet(() -> createNaverLink(user, profile.id(), providerEmail));
  }

  public void unlinkNaver(Long userId) {
    User user = getActiveUser(userId);
    SocialAccount socialAccount =
        socialAccountRepository
            .findByUserIdAndProvider(userId, SocialProvider.NAVER)
            .orElseThrow(() -> new BusinessException(SocialAccountErrorCode.NOT_LINKED));

    long linkedSocialAccountCount = socialAccountRepository.countByUserId(userId);
    if (!user.isPasswordLoginEnabled() && linkedSocialAccountCount <= 1) {
      throw new BusinessException(SocialAccountErrorCode.LAST_LOGIN_METHOD);
    }

    socialAccountRepository.delete(socialAccount);
    socialAccountRepository.flush();
  }

  private SocialAccountResponseDto createNaverLink(
      User user, String providerUserId, String providerEmail) {
    socialAccountRepository
        .findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId)
        .ifPresent(
            account -> {
              throw new BusinessException(SocialAccountErrorCode.LINKED_TO_OTHER_USER);
            });

    SocialAccount saved =
        socialAccountRepository.saveAndFlush(
            SocialAccount.create(user, SocialProvider.NAVER, providerUserId, providerEmail));
    return toLinkedResponse(saved);
  }

  private SocialAccountResponseDto validateAndReturnExisting(
      SocialAccount existing, String providerUserId) {
    if (!existing.getProviderUserId().equals(providerUserId)) {
      throw new BusinessException(SocialAccountErrorCode.ALREADY_LINKED);
    }
    return toLinkedResponse(existing);
  }

  private void validateProviderEmail(User user, String providerEmail) {
    if (!normalizeEmail(user.getEmail()).equals(providerEmail)) {
      throw new BusinessException(SocialAccountErrorCode.EMAIL_MISMATCH);
    }
  }

  private User getActiveUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    if (!user.isActive()) {
      throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
    }
    return user;
  }

  private SocialAccountResponseDto toResponse(
      SocialProvider provider, SocialAccount socialAccount) {
    if (socialAccount == null) {
      return new SocialAccountResponseDto(provider, false, null, null);
    }
    return toLinkedResponse(socialAccount);
  }

  private SocialAccountResponseDto toLinkedResponse(SocialAccount socialAccount) {
    return new SocialAccountResponseDto(
        socialAccount.getProvider(),
        true,
        socialAccount.getProviderEmail(),
        socialAccount.getCreatedAt());
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
