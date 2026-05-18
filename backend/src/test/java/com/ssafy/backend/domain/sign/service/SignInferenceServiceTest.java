package com.ssafy.backend.domain.sign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignInferenceServiceTest {

  private static final int SEQUENCE_LENGTH = 30;
  private static final int FEATURE_DIMENSION = 332;

  @Mock private SignAiClient signAiClient;

  private SignInferenceService signInferenceService;

  @BeforeEach
  void setUp() {
    signInferenceService = new SignInferenceService(signAiClient);
  }

  @Test
  @DisplayName("기본 sequenceLength, featureDimension, topK를 채워 AI 서버에 요청한다")
  void predictNormalizesDefaultRequestValues() {
    SignInferenceResponseDto expectedResponse = response();
    when(signAiClient.predict(any(SignInferenceRequestDto.class))).thenReturn(expectedResponse);

    SignInferenceRequestDto request =
        new SignInferenceRequestDto(null, null, null, features(), null, null);

    SignInferenceResponseDto response = signInferenceService.predict(request);

    assertThat(response).isEqualTo(expectedResponse);
    ArgumentCaptor<SignInferenceRequestDto> captor =
        ArgumentCaptor.forClass(SignInferenceRequestDto.class);
    verify(signAiClient).predict(captor.capture());
    assertThat(captor.getValue().sequenceLength()).isEqualTo(SEQUENCE_LENGTH);
    assertThat(captor.getValue().featureDimension()).isEqualTo(FEATURE_DIMENSION);
    assertThat(captor.getValue().topK()).isEqualTo(3);
  }

  @Test
  @DisplayName("현재 모델 규격이 아닌 sequenceLength는 거부한다")
  void predictRejectsUnsupportedSequenceLength() {
    SignInferenceRequestDto request =
        new SignInferenceRequestDto(null, 29, FEATURE_DIMENSION, features(), null, 3);

    assertSignInferenceError(request, SignInferenceErrorCode.INVALID_FEATURE_SEQUENCE);
    verifyNoInteractions(signAiClient);
  }

  @Test
  @DisplayName("features 길이가 30 * 332와 다르면 거부한다")
  void predictRejectsInvalidFeatureLength() {
    SignInferenceRequestDto request =
        new SignInferenceRequestDto(
            null, SEQUENCE_LENGTH, FEATURE_DIMENSION, Collections.nCopies(10, 0.0f), null, 3);

    assertSignInferenceError(request, SignInferenceErrorCode.INVALID_FEATURE_SEQUENCE);
    verifyNoInteractions(signAiClient);
  }

  @Test
  @DisplayName("timestampsMs 길이가 sequenceLength와 다르면 거부한다")
  void predictRejectsInvalidTimestampLength() {
    SignInferenceRequestDto request =
        new SignInferenceRequestDto(
            null, SEQUENCE_LENGTH, FEATURE_DIMENSION, features(), Collections.nCopies(10, 0L), 3);

    assertSignInferenceError(request, SignInferenceErrorCode.INVALID_FEATURE_SEQUENCE);
    verifyNoInteractions(signAiClient);
  }

  private void assertSignInferenceError(
      SignInferenceRequestDto request, SignInferenceErrorCode expectedErrorCode) {
    assertThatThrownBy(() -> signInferenceService.predict(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  private List<Float> features() {
    return Collections.nCopies(SEQUENCE_LENGTH * FEATURE_DIMENSION, 0.0f);
  }

  private SignInferenceResponseDto response() {
    return new SignInferenceResponseDto(
        "병원", 0.92f, 0.2f, 19, "병원", true, null, List.of(), "v6_24words_tcn", 18L);
  }
}
