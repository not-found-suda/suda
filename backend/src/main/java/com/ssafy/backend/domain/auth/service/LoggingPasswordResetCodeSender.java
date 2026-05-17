package com.ssafy.backend.domain.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingPasswordResetCodeSender implements PasswordResetCodeSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetCodeSender.class);

  private final PasswordResetProperties passwordResetProperties;

  public LoggingPasswordResetCodeSender(PasswordResetProperties passwordResetProperties) {
    this.passwordResetProperties = passwordResetProperties;
  }

  @Override
  public void send(String email, String code, long expiresInSeconds) {
    if (!passwordResetProperties.isLogCodeEnabled()) {
      log.info(
          "Password reset code generated. email={}, expiresInSeconds={}", email, expiresInSeconds);
      return;
    }

    log.info(
        "Password reset code generated. email={}, code={}, expiresInSeconds={}",
        email,
        code,
        expiresInSeconds);
  }
}
