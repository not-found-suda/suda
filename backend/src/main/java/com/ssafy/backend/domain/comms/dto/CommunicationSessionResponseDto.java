package com.ssafy.backend.domain.comms.dto;

import com.ssafy.backend.domain.comms.entity.CommunicationSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CommunicationSessionResponseDto(
    @Schema(description = "대화 세션 ID", example = "1") Long sessionId,
    @Schema(description = "사용자 ID", example = "1") Long userId,
    @Schema(description = "아이 프로필 ID", example = "1") Long childProfileId,
    @Schema(description = "세션 상태", example = "ACTIVE") CommunicationSessionStatus status,
    @Schema(description = "대화 시작 시각") LocalDateTime startedAt,
    @Schema(description = "대화 종료 시각") LocalDateTime endedAt,
    @Schema(description = "저장된 메시지 개수", example = "0") int messageCount,
    @Schema(description = "원본 로그 보관 만료 시각") LocalDateTime expiresAt) {}
