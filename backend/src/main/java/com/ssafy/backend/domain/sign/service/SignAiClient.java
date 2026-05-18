package com.ssafy.backend.domain.sign.service;

import com.ssafy.backend.domain.sign.config.SignAiProperties;
import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SignAiClient {

  private static final Logger log = LoggerFactory.getLogger(SignAiClient.class);
  private static final String PREDICT_PATH = "/internal/sign/predict";

  private final RestClient restClient;
  private final SignAiProperties properties;

  public SignAiClient(
      @Qualifier("signAiRestClient") RestClient restClient, SignAiProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public SignInferenceResponseDto predict(SignInferenceRequestDto requestDto) {
    URI uri = properties.baseUrl().resolve(PREDICT_PATH);

    try {
      SignInferenceResponseDto response =
          restClient
              .post()
              .uri(uri)
              .body(requestDto)
              .retrieve()
              .body(SignInferenceResponseDto.class);
      if (response == null) {
        throw new BusinessException(SignInferenceErrorCode.AI_SERVER_UNAVAILABLE);
      }
      return response;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RestClientException exception) {
      log.warn("Sign AI server request failed. uri={}, message={}", uri, exception.getMessage());
      throw new BusinessException(SignInferenceErrorCode.AI_SERVER_UNAVAILABLE);
    }
  }
}
