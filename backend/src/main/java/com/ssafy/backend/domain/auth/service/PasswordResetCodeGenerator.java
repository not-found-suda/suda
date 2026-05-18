package com.ssafy.backend.domain.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetCodeGenerator {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int RESET_TOKEN_BYTE_LENGTH = 32;

  public String generateCode(int length) {
    int upperBound = (int) Math.pow(10, length);
    int code = SECURE_RANDOM.nextInt(upperBound);
    return String.format("%0" + length + "d", code);
  }

  public String generateResetToken() {
    byte[] randomBytes = new byte[RESET_TOKEN_BYTE_LENGTH];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
