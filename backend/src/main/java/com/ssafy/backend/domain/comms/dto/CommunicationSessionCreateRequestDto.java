package com.ssafy.backend.domain.comms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CommunicationSessionCreateRequestDto(
    @Schema(description = "대화 대상 아이 프로필 ID", example = "1")
        @NotNull(message = "childProfileId는 필수입니다.")
        Long childProfileId) {}
