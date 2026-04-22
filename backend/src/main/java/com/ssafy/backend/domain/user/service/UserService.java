package com.ssafy.backend.domain.user.service;

import com.ssafy.backend.domain.user.dto.UserAuthResponseDto;
import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public UserAuthResponseDto getAuthInfoByEmail(String email) {
    String normalizedEmail = normalizeEmail(email);
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    return new UserAuthResponseDto(
        user.getId(), user.getEmail(), user.getPassword(), user.isActive(), user.getRole());
  }

  @Transactional(readOnly = true)
  public UserAuthResponseDto getAuthInfoById(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    return new UserAuthResponseDto(
        user.getId(), user.getEmail(), user.getPassword(), user.isActive(), user.getRole());
  }

  @Transactional(readOnly = true)
  public boolean existsByEmail(String email) {
    String normalizedEmail = normalizeEmail(email);
    if (normalizedEmail.isBlank()) {
      return false;
    }
    return userRepository.existsByEmailIgnoreCase(normalizedEmail);
  }

  public Long register(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    User saved =
        userRepository.save(User.create(normalizedEmail, passwordEncoder.encode(password)));
    return saved.getId();
  }

  @Transactional(readOnly = true)
  public UserResponseDto getUserById(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    return new UserResponseDto(user.getId(), user.getEmail(), user.isActive(), user.getRole());
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
