package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.domain.auth.exception.OAuthErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverOAuthClient {

  private static final String NAVER_PROFILE_URL = "https://openapi.naver.com/v1/nid/me";
  private static final String SUCCESS_RESULT_CODE = "00";

  private final RestClient restClient;

  public NaverOAuthClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public NaverProfile getProfile(String providerAccessToken) {
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
        throw new BusinessException(OAuthErrorCode.INVALID_PROVIDER_TOKEN);
      }

      NaverProfile profile = response.response();
      if (isBlank(profile.id()) || isBlank(profile.email())) {
        throw new BusinessException(OAuthErrorCode.INVALID_PROVIDER_TOKEN);
      }
      return profile;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().is4xxClientError()) {
        throw new BusinessException(OAuthErrorCode.INVALID_PROVIDER_TOKEN);
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

  public record NaverProfile(String id, String email, String nickname, String name) {}
}
