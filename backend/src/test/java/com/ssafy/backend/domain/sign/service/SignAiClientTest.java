package com.ssafy.backend.domain.sign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssafy.backend.domain.sign.config.SignAiProperties;
import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SignAiClientTest {

  private static final String PREDICT_URL = "http://sign-ai.test/internal/sign/predict";

  private MockRestServiceServer server;
  private SignAiClient signAiClient;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    signAiClient =
        new SignAiClient(
            builder.build(),
            new SignAiProperties(
                URI.create("http://sign-ai.test"),
                new SignAiProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(1))));
  }

  @Test
  @DisplayName("AI 서버 응답을 수어 인식 응답으로 반환한다")
  void predictReturnsAiServerResponse() {
    server
        .expect(once(), requestTo(PREDICT_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {
                  "gloss": "병원",
                  "confidence": 0.92,
                  "margin": 0.2,
                  "classIndex": 19,
                  "rawGloss": "병원",
                  "accepted": true,
                  "rejectionReason": null,
                  "topCandidates": [],
                  "modelVersion": "v6_24words_tcn",
                  "inferenceMs": 18
                }
                """,
                MediaType.APPLICATION_JSON));

    SignInferenceResponseDto response = signAiClient.predict(request());

    assertThat(response.gloss()).isEqualTo("병원");
    assertThat(response.accepted()).isTrue();
    assertThat(response.modelVersion()).isEqualTo("v6_24words_tcn");
    server.verify();
  }

  @Test
  @DisplayName("AI 서버 503 응답은 AI 서버 사용 불가 오류로 변환한다")
  void predictMapsAiServerUnavailable() {
    server
        .expect(once(), requestTo(PREDICT_URL))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("{\"detail\":\"loading\"}"));

    assertSignAiError(SignInferenceErrorCode.AI_SERVER_UNAVAILABLE);
    server.verify();
  }

  @Test
  @DisplayName("AI 서버 422 응답은 AI 서버 요청 거부 오류로 변환한다")
  void predictMapsAiServerRejectedRequest() {
    server
        .expect(once(), requestTo(PREDICT_URL))
        .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{\"detail\":\"invalid\"}"));

    assertSignAiError(SignInferenceErrorCode.AI_SERVER_REJECTED_REQUEST);
    server.verify();
  }

  @Test
  @DisplayName("AI 서버 504 응답은 timeout 오류로 변환한다")
  void predictMapsGatewayTimeout() {
    server
        .expect(once(), requestTo(PREDICT_URL))
        .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

    assertSignAiError(SignInferenceErrorCode.AI_SERVER_TIMEOUT);
    server.verify();
  }

  @Test
  @DisplayName("AI 서버 5xx 응답은 AI 서버 사용 불가 오류로 변환한다")
  void predictMapsAiServerError() {
    server.expect(once(), requestTo(PREDICT_URL)).andRespond(withServerError());

    assertSignAiError(SignInferenceErrorCode.AI_SERVER_UNAVAILABLE);
    server.verify();
  }

  @Test
  @DisplayName("AI 서버 empty body 응답은 잘못된 응답 오류로 변환한다")
  void predictMapsEmptyResponseBody() {
    server.expect(once(), requestTo(PREDICT_URL)).andRespond(withNoContent());

    assertSignAiError(SignInferenceErrorCode.AI_SERVER_INVALID_RESPONSE);
    server.verify();
  }

  @Test
  @DisplayName("AI 서버 socket timeout은 timeout 오류로 변환한다")
  void predictMapsSocketTimeout() {
    server
        .expect(once(), requestTo(PREDICT_URL))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("read timed out");
            });

    assertSignAiError(SignInferenceErrorCode.AI_SERVER_TIMEOUT);
    server.verify();
  }

  private void assertSignAiError(SignInferenceErrorCode expectedErrorCode) {
    assertThatThrownBy(() -> signAiClient.predict(request()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  private SignInferenceRequestDto request() {
    return new SignInferenceRequestDto(null, 30, 332, Collections.nCopies(30 * 332, 0.0f), null, 3);
  }
}
