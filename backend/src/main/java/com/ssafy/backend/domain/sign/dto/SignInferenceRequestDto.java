package com.ssafy.backend.domain.sign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SignInferenceRequestDto(
    @Schema(description = "모델 버전", example = "v6-tcn") String modelVersion,
    @Schema(description = "시퀀스 프레임 수", example = "30")
        @Positive(message = "sequenceLength는 1 이상이어야 합니다.")
        Integer sequenceLength,
    @Schema(description = "프레임당 feature 차원", example = "332")
        @Positive(message = "featureDimension은 1 이상이어야 합니다.")
        Integer featureDimension,
    @Schema(description = "flatten된 feature sequence. 기본 규격은 30 * 332 = 9960개입니다.")
        @NotEmpty(message = "features는 비어 있을 수 없습니다.")
        List<@NotNull(message = "feature 값은 null일 수 없습니다.") Float> features,
    @Schema(description = "프레임별 timestamp 목록") List<Long> timestampsMs,
    @Schema(description = "반환할 후보 개수", example = "3") @Positive(message = "topK는 1 이상이어야 합니다.")
        Integer topK) {}
