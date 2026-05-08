package com.ssafy.backend.domain.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.backend.domain.auth.config.OAuthProperties;
import com.ssafy.backend.domain.auth.exception.OAuthErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverOAuthClient {

  private static final String NAVER_PROFILE_URL = "https://openapi.naver.com/v1/nid/me";
  private static final String NAVER_TOKEN_URL = "https://nid.naver.com/oauth2/token";
  private static final String SUCCESS_RESULT_CODE = "00";

  private final RestClient restClient;
  private final OAuthProperties oAuthProperties;

  public NaverOAuthClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  public NaverProfile getProfile(
      String authorizationCode, String state, String codeVerifier, String redirectUri) {
    NaverTokenResponse tokenResponse =
        exchangeToken(authorizationCode, state, codeVerifier, redirectUri);
    if (tokenResponse == null || isBlank(tokenResponse.accessToken())) {
      throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
    }
    return getProfileByAccessToken(tokenResponse.accessToken());
  }

  private NaverTokenResponse exchangeToken(
      String authorizationCode, String state, String codeVerifier, String redirectUri) {
    OAuthProperties.Naver naver = oAuthProperties.naver();
    if (naver == null || isBlank(naver.clientId()) || isBlank(naver.clientSecret())) {
      throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", naver.clientId());
    form.add("client_secret", naver.clientSecret());
    form.add("redirect_uri", redirectUri);
    form.add("code", authorizationCode);
    form.add("state", state);
    form.add("code_verifier", codeVerifier);

    try {
      NaverTokenResponse response =
          restClient
              .post()
              .uri(NAVER_TOKEN_URL)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(NaverTokenResponse.class);

      if (response == null || !isBlank(response.error())) {
        throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
      }
      return response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().is4xxClientError()) {
        throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
      }
      throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
    } catch (RestClientException exception) {
      throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
    }
  }

  private NaverProfile getProfileByAccessToken(String providerAccessToken) {
    try {
      NaverProfileResponse response =
          restClient
              .get()
              .uri(NAVER_PROFILE_URL)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerAccessToken)
              .retrieve()
              .body(NaverProfileResponse.class);

      if (response == null
          || !SUCCESS_RESULT_CODE.equals(response.resultcode())
          || response.response() == null) {
        throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
      }

      NaverProfile profile = response.response();
      if (isBlank(profile.id()) || isBlank(profile.email())) {
        throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
      }
      return profile;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 401) {
        throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
      }
      throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
    } catch (RestClientException exception) {
      throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record NaverProfileResponse(String resultcode, String message, NaverProfile response) {}

  private record NaverTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn,
      String error,
      @JsonProperty("error_description") String errorDescription) {}

  public record NaverProfile(String id, String email, String nickname, String name) {}
}
