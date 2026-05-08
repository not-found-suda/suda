package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.dto.OAuthLoginRequestDto;
import com.ssafy.backend.domain.auth.exception.OAuthErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {

  private final NaverOAuthClient naverOAuthClient;
  private final OAuthLoginProcessor oAuthLoginProcessor;

  public OAuthService(NaverOAuthClient naverOAuthClient, OAuthLoginProcessor oAuthLoginProcessor) {
    this.naverOAuthClient = naverOAuthClient;
    this.oAuthLoginProcessor = oAuthLoginProcessor;
  }

  public LoginResponseDto loginWithNaver(OAuthLoginRequestDto requestDto) {
    if (requestDto == null) {
      throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
    }

    NaverOAuthClient.NaverProfile profile =
        naverOAuthClient.getProfile(
            requestDto.authorizationCode(),
            requestDto.state(),
            requestDto.codeVerifier(),
            requestDto.redirectUri());
    return oAuthLoginProcessor.loginWithNaverProfile(profile);
  }
}
