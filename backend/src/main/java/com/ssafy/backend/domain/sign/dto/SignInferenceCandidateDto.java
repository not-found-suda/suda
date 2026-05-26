package com.ssafy.backend.domain.sign.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignInferenceCandidateDto(
    @Schema(description = "후보 순위", example = "1") int rank,
    @Schema(description = "모델 클래스 인덱스", example = "19") int classIndex,
    @Schema(description = "수어 gloss", example = "병원") String gloss,
    @Schema(description = "후보 신뢰도", example = "0.92") float confidence) {}
