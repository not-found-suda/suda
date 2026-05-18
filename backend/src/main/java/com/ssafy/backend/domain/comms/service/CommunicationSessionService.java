package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.comms.dto.CommunicationSessionCreateRequestDto;
import com.ssafy.backend.domain.comms.dto.CommunicationSessionResponseDto;
import com.ssafy.backend.domain.comms.entity.CommunicationSession;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionAnalysis;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionStatus;
import com.ssafy.backend.domain.comms.event.CommunicationSessionEndedEvent;
import com.ssafy.backend.domain.comms.exception.CommunicationSessionErrorCode;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionAnalysisRepository;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunicationSessionService {

  private final CommunicationSessionRepository communicationSessionRepository;
  private final ChildProfileRepository childProfileRepository;
  private final UserRepository userRepository;
  private final CommunicationSessionAnalysisRepository communicationSessionAnalysisRepository;
  private final ApplicationEventPublisher eventPublisher;

  public CommunicationSessionService(
      CommunicationSessionRepository communicationSessionRepository,
      ChildProfileRepository childProfileRepository,
      UserRepository userRepository,
      CommunicationSessionAnalysisRepository communicationSessionAnalysisRepository,
      ApplicationEventPublisher eventPublisher) {
    this.communicationSessionRepository = communicationSessionRepository;
    this.childProfileRepository = childProfileRepository;
    this.userRepository = userRepository;
    this.communicationSessionAnalysisRepository = communicationSessionAnalysisRepository;
    this.eventPublisher = eventPublisher;
  }

  public CommunicationSessionResponseDto createSession(
      Long userId, CommunicationSessionCreateRequestDto requestDto) {
    if (requestDto == null || requestDto.childProfileId() == null) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "childProfileId는 필수입니다.");
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

    ChildProfile childProfile =
        childProfileRepository
            .findByIdAndUserIdAndActiveTrue(requestDto.childProfileId(), userId)
            .orElseThrow(() -> new BusinessException(ChildProfileErrorCode.NOT_FOUND));

    CommunicationSession session =
        communicationSessionRepository.save(CommunicationSession.start(user, childProfile));

    return toResponse(session);
  }

  public CommunicationSessionResponseDto endSession(Long userId, Long sessionId) {
    CommunicationSession session =
        communicationSessionRepository
            .findByIdAndUserId(sessionId, userId)
            .orElseThrow(
                () -> new BusinessException(CommunicationSessionErrorCode.SESSION_NOT_FOUND));

    if (session.getStatus() == CommunicationSessionStatus.ENDED) {
      throw new BusinessException(CommunicationSessionErrorCode.SESSION_ALREADY_ENDED);
    }

    session.end();

    if (!communicationSessionAnalysisRepository.existsBySessionId(session.getId())) {
      communicationSessionAnalysisRepository.save(CommunicationSessionAnalysis.pending(session));
    }

    eventPublisher.publishEvent(new CommunicationSessionEndedEvent(session.getId()));

    return toResponse(session);
  }

  private CommunicationSessionResponseDto toResponse(CommunicationSession session) {
    return new CommunicationSessionResponseDto(
        session.getId(),
        session.getUser().getId(),
        session.getChildProfile().getId(),
        session.getStatus(),
        session.getStartedAt(),
        session.getEndedAt(),
        session.getMessageCount(),
        session.getExpiresAt());
  }
}
