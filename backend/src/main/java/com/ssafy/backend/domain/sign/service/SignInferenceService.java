package com.ssafy.backend.domain.sign.service;

import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class SignInferenceService {

  private static final int DEFAULT_SEQUENCE_LENGTH = 30;
  private static final int DEFAULT_FEATURE_DIMENSION = 332;
  private static final int DEFAULT_TOP_K = 3;
  private static final int MAX_TOP_K = 5;

  private final SignAiClient signAiClient;

  public SignInferenceService(SignAiClient signAiClient) {
    this.signAiClient = signAiClient;
  }

  public SignInferenceResponseDto predict(SignInferenceRequestDto requestDto) {
    SignInferenceRequestDto normalizedRequest = normalize(requestDto);
    validateFeatureSequence(normalizedRequest);
    return signAiClient.predict(normalizedRequest);
  }

  private SignInferenceRequestDto normalize(SignInferenceRequestDto requestDto) {
    int sequenceLength =
        requestDto.sequenceLength() == null ? DEFAULT_SEQUENCE_LENGTH : requestDto.sequenceLength();
    int featureDimension =
        requestDto.featureDimension() == null
            ? DEFAULT_FEATURE_DIMENSION
            : requestDto.featureDimension();
    int topK = requestDto.topK() == null ? DEFAULT_TOP_K : Math.min(requestDto.topK(), MAX_TOP_K);

    return new SignInferenceRequestDto(
        requestDto.modelVersion(),
        sequenceLength,
        featureDimension,
        requestDto.features(),
        requestDto.timestampsMs(),
        topK);
  }

  private void validateFeatureSequence(SignInferenceRequestDto requestDto) {
    if (requestDto.sequenceLength() != DEFAULT_SEQUENCE_LENGTH
        || requestDto.featureDimension() != DEFAULT_FEATURE_DIMENSION) {
      throw new BusinessException(
          SignInferenceErrorCode.INVALID_FEATURE_SEQUENCE,
          "현재 서버 수어 인식 모델은 sequenceLength=30, featureDimension=332 규격만 지원합니다.");
    }

    long expectedSize = (long) requestDto.sequenceLength() * requestDto.featureDimension();
    if (requestDto.features().size() != expectedSize) {
      throw new BusinessException(
          SignInferenceErrorCode.INVALID_FEATURE_SEQUENCE,
          "features 길이는 sequenceLength * featureDimension과 같아야 합니다.");
    }
    if (requestDto.timestampsMs() != null
        && !requestDto.timestampsMs().isEmpty()
        && requestDto.timestampsMs().size() != requestDto.sequenceLength()) {
      throw new BusinessException(
          SignInferenceErrorCode.INVALID_FEATURE_SEQUENCE,
          "timestampsMs 길이는 sequenceLength와 같아야 합니다.");
    }
  }
}
