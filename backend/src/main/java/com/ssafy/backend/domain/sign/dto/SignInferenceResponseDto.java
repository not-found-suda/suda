package com.ssafy.backend.domain.sign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SignInferenceResponseDto(
    @Schema(description = "최종 수어 gloss", example = "병원") String gloss,
    @Schema(description = "top-1 신뢰도", example = "0.92") float confidence,
    @Schema(description = "top-1과 top-2 신뢰도 차이", example = "0.25") float margin,
    @Schema(description = "모델 클래스 인덱스", example = "19") Integer classIndex,
    @Schema(description = "threshold 적용 전 원본 gloss", example = "병원") String rawGloss,
    @Schema(description = "모바일에서 사용할 수 있는 예측인지 여부", example = "true") boolean accepted,
    @Schema(description = "거절 사유", example = "low_confidence") String rejectionReason,
    @Schema(description = "상위 후보 목록") List<SignInferenceCandidateDto> topCandidates,
    @Schema(description = "서버 모델 버전", example = "v6-tcn") String modelVersion,
    @Schema(description = "AI 서버 추론 시간(ms)", example = "18") Long inferenceMs,
    @Schema(description = "요청 추적 ID", example = "9f8d7c6b5a4e3210") String traceId) {}
