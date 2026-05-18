package com.ssafy.backend.domain.user.controller;

import com.ssafy.backend.domain.social.dto.SocialAccountLinkRequestDto;
import com.ssafy.backend.domain.social.dto.SocialAccountListResponseDto;
import com.ssafy.backend.domain.social.dto.SocialAccountResponseDto;
import com.ssafy.backend.domain.social.service.SocialAccountService;
import com.ssafy.backend.domain.user.docs.UserApiDocs;
import com.ssafy.backend.domain.user.dto.TtsSpeakerListResponseDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerUpdateRequestDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerUpdateResponseDto;
import com.ssafy.backend.domain.user.dto.UserPasswordChangeRequestDto;
import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.domain.user.dto.UserUpdateRequestDto;
import com.ssafy.backend.domain.user.dto.UserUpdateResponseDto;
import com.ssafy.backend.domain.user.dto.UserWithdrawRequestDto;
import com.ssafy.backend.domain.user.service.UserService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserApiDocs {

  private final UserService userService;
  private final SocialAccountService socialAccountService;

  public UserController(UserService userService, SocialAccountService socialAccountService) {
    this.userService = userService;
    this.socialAccountService = socialAccountService;
  }

  @GetMapping("/me")
  @Override
  public ResponseEntity<UserResponseDto> me(Authentication authentication) {
    Long userId = extractUserId(authentication);

    UserResponseDto responseDto = userService.getUserById(userId);
    return ResponseEntity.ok(responseDto);
  }

  @PatchMapping("/me")
  @Override
  public ResponseEntity<UserUpdateResponseDto> updateMe(
      Authentication authentication, @Valid @RequestBody UserUpdateRequestDto requestDto) {
    Long userId = extractUserId(authentication);

    UserUpdateResponseDto responseDto = userService.updateUser(userId, requestDto.name());
    return ResponseEntity.ok(responseDto);
  }

  @PatchMapping("/me/password")
  @Override
  public ResponseEntity<Void> changePassword(
      Authentication authentication, @Valid @RequestBody UserPasswordChangeRequestDto requestDto) {
    Long userId = extractUserId(authentication);

    userService.changePassword(userId, requestDto.currentPassword(), requestDto.newPassword());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/me")
  @Override
  public ResponseEntity<Void> withdrawMe(
      Authentication authentication,
      @RequestBody(required = false) UserWithdrawRequestDto requestDto) {
    Long userId = extractUserId(authentication);
    String password = requestDto != null ? requestDto.password() : null;

    userService.withdraw(userId, password);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me/tts-speakers")
  @Override
  public ResponseEntity<TtsSpeakerListResponseDto> getTtsSpeakers(Authentication authentication) {
    extractUserId(authentication);

    return ResponseEntity.ok(userService.getTtsSpeakers());
  }

  @PatchMapping("/me/tts-speaker")
  @Override
  public ResponseEntity<TtsSpeakerUpdateResponseDto> updateTtsSpeaker(
      Authentication authentication, @Valid @RequestBody TtsSpeakerUpdateRequestDto requestDto) {
    Long userId = extractUserId(authentication);

    return ResponseEntity.ok(userService.updateTtsSpeaker(userId, requestDto.ttsSpeaker()));
  }

  @GetMapping("/me/social-accounts")
  @Override
  public ResponseEntity<SocialAccountListResponseDto> getSocialAccounts(
      Authentication authentication) {
    Long userId = extractUserId(authentication);

    return ResponseEntity.ok(socialAccountService.getMySocialAccounts(userId));
  }

  @PostMapping("/me/social-accounts/naver")
  @Override
  public ResponseEntity<SocialAccountResponseDto> linkNaver(
      Authentication authentication, @Valid @RequestBody SocialAccountLinkRequestDto requestDto) {
    Long userId = extractUserId(authentication);

    return ResponseEntity.ok(
        socialAccountService.linkNaver(userId, requestDto.providerAccessToken()));
  }

  @DeleteMapping("/me/social-accounts/naver")
  @Override
  public ResponseEntity<Void> unlinkNaver(Authentication authentication) {
    Long userId = extractUserId(authentication);

    socialAccountService.unlinkNaver(userId);
    return ResponseEntity.noContent().build();
  }

  private Long extractUserId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
