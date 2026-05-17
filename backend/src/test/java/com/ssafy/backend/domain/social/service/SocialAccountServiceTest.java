package com.ssafy.backend.domain.social.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.auth.service.NaverOAuthClient;
import com.ssafy.backend.domain.social.dto.SocialAccountListResponseDto;
import com.ssafy.backend.domain.social.dto.SocialAccountResponseDto;
import com.ssafy.backend.domain.social.entity.SocialAccount;
import com.ssafy.backend.domain.social.entity.SocialProvider;
import com.ssafy.backend.domain.social.exception.SocialAccountErrorCode;
import com.ssafy.backend.domain.social.repository.SocialAccountRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long OTHER_USER_ID = 2L;
  private static final String PROVIDER_ACCESS_TOKEN = "provider-access-token";
  private static final String NAVER_PROVIDER_USER_ID = "naver-123";
  private static final String OTHER_NAVER_PROVIDER_USER_ID = "naver-456";
  private static final String PROVIDER_EMAIL = "guardian@naver.com";

  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private UserRepository userRepository;
  @Mock private NaverOAuthClient naverOAuthClient;

  private SocialAccountService socialAccountService;

  @BeforeEach
  void setUp() {
    socialAccountService =
        new SocialAccountService(socialAccountRepository, userRepository, naverOAuthClient);
  }

  @Test
  @DisplayName("소셜 계정 목록은 전체 provider의 연동 상태를 반환한다")
  void getMySocialAccountsReturnsAllProviderStatuses() {
    User user = passwordUser(USER_ID);
    SocialAccount naverAccount = naverAccount(user, NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(socialAccountRepository.findByUserId(USER_ID)).thenReturn(List.of(naverAccount));

    SocialAccountListResponseDto response = socialAccountService.getMySocialAccounts(USER_ID);

    assertThat(response.accounts()).hasSize(2);
    assertThat(response.accounts())
        .anySatisfy(
            account -> {
              assertThat(account.provider()).isEqualTo(SocialProvider.NAVER);
              assertThat(account.linked()).isTrue();
              assertThat(account.providerEmail()).isEqualTo(PROVIDER_EMAIL);
            })
        .anySatisfy(
            account -> {
              assertThat(account.provider()).isEqualTo(SocialProvider.KAKAO);
              assertThat(account.linked()).isFalse();
              assertThat(account.providerEmail()).isNull();
            });
  }

  @Test
  @DisplayName("네이버 provider token이 유효하면 현재 사용자에게 네이버 계정을 연동한다")
  void linkNaverCreatesSocialAccount() {
    User user = passwordUser(USER_ID);
    NaverOAuthClient.NaverProfile profile = naverProfile(NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(naverOAuthClient.getProfile(PROVIDER_ACCESS_TOKEN)).thenReturn(profile);
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.empty());
    when(socialAccountRepository.findByProviderAndProviderUserId(
            SocialProvider.NAVER, NAVER_PROVIDER_USER_ID))
        .thenReturn(Optional.empty());
    when(socialAccountRepository.saveAndFlush(Mockito.any(SocialAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SocialAccountResponseDto response =
        socialAccountService.linkNaver(USER_ID, PROVIDER_ACCESS_TOKEN);

    assertThat(response.provider()).isEqualTo(SocialProvider.NAVER);
    assertThat(response.linked()).isTrue();
    assertThat(response.providerEmail()).isEqualTo(PROVIDER_EMAIL);
    verify(socialAccountRepository).saveAndFlush(Mockito.any(SocialAccount.class));
  }

  @Test
  @DisplayName("이미 같은 네이버 계정이 현재 사용자에게 연동되어 있으면 기존 연동 상태를 반환한다")
  void linkNaverReturnsExistingLinkWhenSameAccountAlreadyLinked() {
    User user = passwordUser(USER_ID);
    SocialAccount existing = naverAccount(user, NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(naverOAuthClient.getProfile(PROVIDER_ACCESS_TOKEN))
        .thenReturn(naverProfile(NAVER_PROVIDER_USER_ID));
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.of(existing));

    SocialAccountResponseDto response =
        socialAccountService.linkNaver(USER_ID, PROVIDER_ACCESS_TOKEN);

    assertThat(response.provider()).isEqualTo(SocialProvider.NAVER);
    assertThat(response.linked()).isTrue();
    verify(socialAccountRepository, never()).saveAndFlush(Mockito.any());
  }

  @Test
  @DisplayName("현재 사용자에게 다른 네이버 계정이 이미 연동되어 있으면 추가 연동을 거부한다")
  void linkNaverRejectsWhenUserAlreadyHasDifferentNaverAccount() {
    User user = passwordUser(USER_ID);
    SocialAccount existing = naverAccount(user, OTHER_NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(naverOAuthClient.getProfile(PROVIDER_ACCESS_TOKEN))
        .thenReturn(naverProfile(NAVER_PROVIDER_USER_ID));
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.of(existing));

    assertSocialAccountError(
        () -> socialAccountService.linkNaver(USER_ID, PROVIDER_ACCESS_TOKEN),
        SocialAccountErrorCode.ALREADY_LINKED);

    verify(socialAccountRepository, never()).saveAndFlush(Mockito.any());
  }

  @Test
  @DisplayName("다른 사용자에게 연동된 네이버 계정은 연동할 수 없다")
  void linkNaverRejectsAccountLinkedToOtherUser() {
    User user = passwordUser(USER_ID);
    User otherUser = passwordUser(OTHER_USER_ID);
    SocialAccount otherAccount = naverAccount(otherUser, NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(naverOAuthClient.getProfile(PROVIDER_ACCESS_TOKEN))
        .thenReturn(naverProfile(NAVER_PROVIDER_USER_ID));
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.empty());
    when(socialAccountRepository.findByProviderAndProviderUserId(
            SocialProvider.NAVER, NAVER_PROVIDER_USER_ID))
        .thenReturn(Optional.of(otherAccount));

    assertSocialAccountError(
        () -> socialAccountService.linkNaver(USER_ID, PROVIDER_ACCESS_TOKEN),
        SocialAccountErrorCode.LINKED_TO_OTHER_USER);

    verify(socialAccountRepository, never()).saveAndFlush(Mockito.any());
  }

  @Test
  @DisplayName("비밀번호 로그인 계정은 네이버 연동을 해제할 수 있다")
  void unlinkNaverDeletesSocialAccountForPasswordUser() {
    User user = passwordUser(USER_ID);
    SocialAccount account = naverAccount(user, NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.of(account));
    when(socialAccountRepository.countByUserId(USER_ID)).thenReturn(1L);

    socialAccountService.unlinkNaver(USER_ID);

    verify(socialAccountRepository).delete(account);
    verify(socialAccountRepository).flush();
  }

  @Test
  @DisplayName("소셜 전용 계정은 마지막 로그인 수단인 네이버 연동을 해제할 수 없다")
  void unlinkNaverRejectsLastLoginMethod() {
    User user = oauthOnlyUser(USER_ID);
    SocialAccount account = naverAccount(user, NAVER_PROVIDER_USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.of(account));
    when(socialAccountRepository.countByUserId(USER_ID)).thenReturn(1L);

    assertSocialAccountError(
        () -> socialAccountService.unlinkNaver(USER_ID), SocialAccountErrorCode.LAST_LOGIN_METHOD);

    verify(socialAccountRepository, never()).delete(Mockito.any());
    verify(socialAccountRepository, never()).flush();
  }

  @Test
  @DisplayName("연동된 네이버 계정이 없으면 해제를 거부한다")
  void unlinkNaverRejectsMissingLink() {
    User user = passwordUser(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.NAVER))
        .thenReturn(Optional.empty());

    assertSocialAccountError(
        () -> socialAccountService.unlinkNaver(USER_ID), SocialAccountErrorCode.NOT_LINKED);

    verify(socialAccountRepository, never()).delete(Mockito.any());
    verify(socialAccountRepository, never()).flush();
  }

  private User passwordUser(Long userId) {
    User user = User.create("guardian" + userId + "@example.com", "encoded-password", "보호자");
    ReflectionTestUtils.setField(user, "id", userId);
    return user;
  }

  private User oauthOnlyUser(Long userId) {
    User user =
        User.createOAuthUser("guardian" + userId + "@example.com", "encoded-password", "보호자");
    ReflectionTestUtils.setField(user, "id", userId);
    return user;
  }

  private SocialAccount naverAccount(User user, String providerUserId) {
    return SocialAccount.create(user, SocialProvider.NAVER, providerUserId, PROVIDER_EMAIL);
  }

  private NaverOAuthClient.NaverProfile naverProfile(String providerUserId) {
    return new NaverOAuthClient.NaverProfile(providerUserId, PROVIDER_EMAIL, "네이버닉네임", "네이버이름");
  }

  private void assertSocialAccountError(
      ThrowingCall call, SocialAccountErrorCode expectedErrorCode) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
