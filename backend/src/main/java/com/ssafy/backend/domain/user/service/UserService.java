package com.ssafy.backend.domain.user.service;

import com.ssafy.backend.domain.auth.service.UserTokenInvalidationService;
import com.ssafy.backend.domain.user.dto.TtsSpeakerListResponseDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerResponseDto;
import com.ssafy.backend.domain.user.dto.TtsSpeakerUpdateResponseDto;
import com.ssafy.backend.domain.user.dto.UserAuthResponseDto;
import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.domain.user.dto.UserUpdateResponseDto;
import com.ssafy.backend.domain.user.entity.TtsSpeaker;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserTokenInvalidationService userTokenInvalidationService;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      UserTokenInvalidationService userTokenInvalidationService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.userTokenInvalidationService = userTokenInvalidationService;
  }

  @Transactional(readOnly = true)
  public UserAuthResponseDto getAuthInfoByEmail(String email) {
    String normalizedEmail = normalizeEmail(email);
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    return new UserAuthResponseDto(
        user.getId(),
        user.getEmail(),
        user.getPassword(),
        user.isPasswordLoginEnabled(),
        user.isActive(),
        user.getRole());
  }

  @Transactional(readOnly = true)
  public UserAuthResponseDto getAuthInfoById(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    return new UserAuthResponseDto(
        user.getId(),
        user.getEmail(),
        user.getPassword(),
        user.isPasswordLoginEnabled(),
        user.isActive(),
        user.getRole());
  }

  @Transactional(readOnly = true)
  public boolean existsByEmail(String email) {
    String normalizedEmail = normalizeEmail(email);
    if (normalizedEmail.isBlank()) {
      return false;
    }
    return userRepository.existsByEmailIgnoreCase(normalizedEmail);
  }

  public UserResponseDto register(String email, String password, String name) {
    String normalizedEmail = normalizeEmail(email);
    String normalizedName = normalizeName(name);
    User saved =
        userRepository.save(
            User.create(normalizedEmail, passwordEncoder.encode(password), normalizedName));
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public UserResponseDto getUserById(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    return toResponse(user);
  }

  public UserUpdateResponseDto updateUser(Long userId, String name) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    user.updateName(normalizeName(name));
    userRepository.flush();
    return toUpdateResponse(user);
  }

  public void changePassword(Long userId, String currentPassword, String newPassword) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

    if (!user.isPasswordLoginEnabled()) {
      throw new BusinessException(UserErrorCode.PASSWORD_LOGIN_NOT_ENABLED);
    }
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new BusinessException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
    }
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new BusinessException(UserErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.flush();
  }

  @Transactional(readOnly = true)
  public TtsSpeakerListResponseDto getTtsSpeakers() {
    return new TtsSpeakerListResponseDto(
        Arrays.stream(TtsSpeaker.values())
            .map(speaker -> new TtsSpeakerResponseDto(speaker.getCode(), speaker.getLabel()))
            .toList());
  }

  public TtsSpeakerUpdateResponseDto updateTtsSpeaker(Long userId, String ttsSpeaker) {
    String normalizedSpeaker = normalizeTtsSpeaker(ttsSpeaker);

    if (!TtsSpeaker.isSupported(normalizedSpeaker)) {
      throw new BusinessException(UserErrorCode.UNSUPPORTED_TTS_SPEAKER);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

    user.updateTtsSpeaker(normalizedSpeaker);
    userRepository.flush();

    return new TtsSpeakerUpdateResponseDto(user.getId(), user.getTtsSpeaker());
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeName(String name) {
    if (name == null) {
      return "";
    }
    return name.trim();
  }

  private String normalizeTtsSpeaker(String ttsSpeaker) {
    if (ttsSpeaker == null) {
      return "";
    }
    return ttsSpeaker.trim().toLowerCase(Locale.ROOT);
  }

  private UserResponseDto toResponse(User user) {
    return new UserResponseDto(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.isPasswordLoginEnabled(),
        user.isActive(),
        user.getRole(),
        user.getTtsSpeaker());
  }

  private UserUpdateResponseDto toUpdateResponse(User user) {
    return new UserUpdateResponseDto(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.isActive(),
        user.getRole(),
        user.getUpdatedAt());
  }
}
