package com.ssafy.backend.domain.comms.controller;

import com.ssafy.backend.domain.comms.docs.CommunicationSessionApiDocs;
import com.ssafy.backend.domain.comms.dto.CommunicationSessionCreateRequestDto;
import com.ssafy.backend.domain.comms.dto.CommunicationSessionResponseDto;
import com.ssafy.backend.domain.comms.service.CommunicationSessionService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comms/sessions")
public class CommunicationSessionController implements CommunicationSessionApiDocs {

  private final CommunicationSessionService communicationSessionService;

  public CommunicationSessionController(CommunicationSessionService communicationSessionService) {
    this.communicationSessionService = communicationSessionService;
  }

  @PostMapping
  public ResponseEntity<CommunicationSessionResponseDto> createSession(
      Authentication authentication,
      @Valid @RequestBody CommunicationSessionCreateRequestDto requestDto) {
    CommunicationSessionResponseDto responseDto =
        communicationSessionService.createSession(resolveUserId(authentication), requestDto);

    return ResponseEntity.created(URI.create("/api/v1/comms/sessions/" + responseDto.sessionId()))
        .body(responseDto);
  }

  @PatchMapping("/{sessionId}/end")
  public ResponseEntity<CommunicationSessionResponseDto> endSession(
      Authentication authentication, @PathVariable Long sessionId) {
    CommunicationSessionResponseDto responseDto =
        communicationSessionService.endSession(resolveUserId(authentication), sessionId);

    return ResponseEntity.ok(responseDto);
  }

  private Long resolveUserId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
