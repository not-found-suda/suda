package com.ssafy.backend.domain.comms.docs;

import com.ssafy.backend.domain.comms.dto.CommunicationSessionCreateRequestDto;
import com.ssafy.backend.domain.comms.dto.CommunicationSessionResponseDto;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public interface CommunicationSessionApiDocs {

  @Operation(
      summary = "대화 세션 시작",
      description = "comms 화면 진입 시 부모-아이 대화 세션을 시작합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  ResponseEntity<CommunicationSessionResponseDto> createSession(
      Authentication authentication,
      @Valid @RequestBody CommunicationSessionCreateRequestDto requestDto);

  @Operation(
      summary = "대화 세션 종료",
      description = "comms 화면 종료 시 대화 세션을 종료합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "COMMON_UNAUTHORIZED",
    "COMMUNICATION_SESSION_NOT_FOUND",
    "COMMUNICATION_SESSION_ALREADY_ENDED"
  })
  ResponseEntity<CommunicationSessionResponseDto> endSession(
      Authentication authentication, @PathVariable Long sessionId);
}
