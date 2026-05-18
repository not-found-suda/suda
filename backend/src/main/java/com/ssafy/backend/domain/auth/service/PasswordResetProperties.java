package com.ssafy.backend.domain.auth.service;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.password-reset")
public class PasswordResetProperties {

  @Positive private int codeLength = 6;

  @Positive private long codeTtlSeconds = 300;

  @Positive private long resetTokenTtlSeconds = 600;

  @Positive private int maxVerifyAttempts = 5;

  @Positive private long requestCooldownSeconds = 60;

  private boolean logCodeEnabled = false;

  public int getCodeLength() {
    return codeLength;
  }

  public void setCodeLength(int codeLength) {
    this.codeLength = codeLength;
  }

  public long getCodeTtlSeconds() {
    return codeTtlSeconds;
  }

  public void setCodeTtlSeconds(long codeTtlSeconds) {
    this.codeTtlSeconds = codeTtlSeconds;
  }

  public long getResetTokenTtlSeconds() {
    return resetTokenTtlSeconds;
  }

  public void setResetTokenTtlSeconds(long resetTokenTtlSeconds) {
    this.resetTokenTtlSeconds = resetTokenTtlSeconds;
  }

  public int getMaxVerifyAttempts() {
    return maxVerifyAttempts;
  }

  public void setMaxVerifyAttempts(int maxVerifyAttempts) {
    this.maxVerifyAttempts = maxVerifyAttempts;
  }

  public long getRequestCooldownSeconds() {
    return requestCooldownSeconds;
  }

  public void setRequestCooldownSeconds(long requestCooldownSeconds) {
    this.requestCooldownSeconds = requestCooldownSeconds;
  }

  public boolean isLogCodeEnabled() {
    return logCodeEnabled;
  }

  public void setLogCodeEnabled(boolean logCodeEnabled) {
    this.logCodeEnabled = logCodeEnabled;
  }
}
