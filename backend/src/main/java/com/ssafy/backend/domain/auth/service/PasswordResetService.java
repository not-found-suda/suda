package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.domain.auth.dto.PasswordResetVerifyResponseDto;
import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.user.dto.UserAuthResponseDto;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.service.UserService;
import com.ssafy.backend.global.exception.BusinessException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PasswordResetService {

  private final UserService userService;
  private final PasswordResetStore passwordResetStore;
  private final PasswordResetCodeGenerator passwordResetCodeGenerator;
  private final PasswordResetCodeSender passwordResetCodeSender;
  private final PasswordResetProperties passwordResetProperties;
  private final UserTokenInvalidationService userTokenInvalidationService;

  public PasswordResetService(
      UserService userService,
      PasswordResetStore passwordResetStore,
      PasswordResetCodeGenerator passwordResetCodeGenerator,
      PasswordResetCodeSender passwordResetCodeSender,
      PasswordResetProperties passwordResetProperties,
      UserTokenInvalidationService userTokenInvalidationService) {
    this.userService = userService;
    this.passwordResetStore = passwordResetStore;
    this.passwordResetCodeGenerator = passwordResetCodeGenerator;
    this.passwordResetCodeSender = passwordResetCodeSender;
    this.passwordResetProperties = passwordResetProperties;
    this.userTokenInvalidationService = userTokenInvalidationService;
  }

  public void request(String email) {
    String normalizedEmail = normalizeEmail(email);
    if (!isResettableAccount(normalizedEmail)) {
      return;
    }
    if (passwordResetStore.hasRequestCooldown(normalizedEmail)) {
      return;
    }

    String code = passwordResetCodeGenerator.generateCode(passwordResetProperties.getCodeLength());
    long ttlSeconds = passwordResetProperties.getCodeTtlSeconds();
    passwordResetStore.saveCode(normalizedEmail, code, Duration.ofSeconds(ttlSeconds));
    passwordResetStore.saveRequestCooldown(
        normalizedEmail, Duration.ofSeconds(passwordResetProperties.getRequestCooldownSeconds()));
    passwordResetCodeSender.send(normalizedEmail, code, ttlSeconds);
  }

  public PasswordResetVerifyResponseDto verify(String email, String code) {
    String normalizedEmail = normalizeEmail(email);
    String storedCode = passwordResetStore.findCodeByEmail(normalizedEmail);
    if (storedCode == null || !storedCode.equals(code) || !isResettableAccount(normalizedEmail)) {
      handleVerifyFailure(normalizedEmail, storedCode);
      throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_RESET_CODE);
    }

    passwordResetStore.deleteCode(normalizedEmail);
    String resetToken = passwordResetCodeGenerator.generateResetToken();
    long ttlSeconds = passwordResetProperties.getResetTokenTtlSeconds();
    passwordResetStore.saveResetToken(resetToken, normalizedEmail, Duration.ofSeconds(ttlSeconds));
    return new PasswordResetVerifyResponseDto(resetToken, ttlSeconds);
  }

  private void handleVerifyFailure(String email, String storedCode) {
    if (storedCode == null) {
      return;
    }

    long attempts =
        passwordResetStore.incrementVerifyAttempt(
            email, Duration.ofSeconds(passwordResetProperties.getCodeTtlSeconds()));
    if (attempts >= passwordResetProperties.getMaxVerifyAttempts()) {
      passwordResetStore.deleteCode(email);
    }
  }

  @Transactional
  public void confirm(String resetToken, String newPassword) {
    String email = passwordResetStore.findEmailByResetToken(resetToken);
    if (email == null) {
      throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }

    Long userId = userService.resetPasswordByEmail(email, newPassword);
    passwordResetStore.deleteResetToken(resetToken);
    userTokenInvalidationService.invalidateAll(userId);
  }

  private boolean isResettableAccount(String email) {
    try {
      UserAuthResponseDto user = userService.getAuthInfoByEmail(email);
      return user.active() && user.passwordLoginEnabled();
    } catch (BusinessException exception) {
      if (exception.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
        return false;
      }
      throw exception;
    }
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
