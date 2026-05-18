package com.ssafy.backend.domain.auth.service;

public interface PasswordResetCodeSender {

  void send(String email, String code, long expiresInSeconds);
}
