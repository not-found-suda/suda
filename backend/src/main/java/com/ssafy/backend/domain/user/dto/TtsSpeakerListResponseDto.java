package com.ssafy.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TtsSpeakerListResponseDto(
    @Schema(description = "선택 가능한 TTS 목소리 목록") List<TtsSpeakerResponseDto> speakers) {}
