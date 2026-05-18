package com.ssafy.backend.domain.sign.service;

import com.ssafy.backend.domain.sign.config.SignAiProperties;
import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SignAiClient {

  private static final Logger log = LoggerFactory.getLogger(SignAiClient.class);
  private static final String PREDICT_PATH = "/internal/sign/predict";
  private static final int MAX_ERROR_BODY_LOG_LENGTH = 1000;

  private final RestClient restClient;
  private final SignAiProperties properties;

  public SignAiClient(
      @Qualifier("signAiRestClient") RestClient restClient, SignAiProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public SignInferenceResponseDto predict(SignInferenceRequestDto requestDto) {
    URI uri = UriComponentsBuilder.fromUri(properties.baseUrl()).path(PREDICT_PATH).build().toUri();

    try {
      SignInferenceResponseDto response =
          restClient
              .post()
              .uri(uri)
              .body(requestDto)
              .retrieve()
              .body(SignInferenceResponseDto.class);
      if (response == null) {
        log.warn("Sign AI server returned empty response. uri={}", uri);
        throw new BusinessException(SignInferenceErrorCode.AI_SERVER_INVALID_RESPONSE);
      }
      return response;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      SignInferenceErrorCode errorCode = resolveResponseErrorCode(exception.getStatusCode());
      log.warn(
          "Sign AI server returned error. uri={}, status={}, code={}, responseBody={}",
          uri,
          exception.getStatusCode(),
          errorCode.getCode(),
          abbreviate(exception.getResponseBodyAsString(), MAX_ERROR_BODY_LOG_LENGTH));
      throw new BusinessException(errorCode);
    } catch (ResourceAccessException exception) {
      SignInferenceErrorCode errorCode =
          hasCause(exception, SocketTimeoutException.class)
              ? SignInferenceErrorCode.AI_SERVER_TIMEOUT
              : SignInferenceErrorCode.AI_SERVER_UNAVAILABLE;
      log.warn(
          "Sign AI server access failed. uri={}, code={}, exceptionClass={}, message={}",
          uri,
          errorCode.getCode(),
          exception.getClass().getName(),
          exception.getMessage());
      throw new BusinessException(errorCode);
    } catch (RestClientException exception) {
      log.warn(
          "Sign AI server response handling failed. uri={}, exceptionClass={}, message={}",
          uri,
          exception.getClass().getName(),
          exception.getMessage());
      throw new BusinessException(SignInferenceErrorCode.AI_SERVER_INVALID_RESPONSE);
    }
  }

  private SignInferenceErrorCode resolveResponseErrorCode(HttpStatusCode statusCode) {
    if (statusCode.value() == 504) {
      return SignInferenceErrorCode.AI_SERVER_TIMEOUT;
    }
    if (statusCode.is4xxClientError()) {
      return SignInferenceErrorCode.AI_SERVER_REJECTED_REQUEST;
    }
    return SignInferenceErrorCode.AI_SERVER_UNAVAILABLE;
  }

  private String abbreviate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength) + "...";
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    Throwable current = throwable;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
