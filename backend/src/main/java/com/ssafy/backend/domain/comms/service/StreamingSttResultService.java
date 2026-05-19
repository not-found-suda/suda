package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.domain.comms.entity.CommunicationMessage;
import com.ssafy.backend.domain.comms.entity.CommunicationSession;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionStatus;
import com.ssafy.backend.domain.comms.repository.CommunicationMessageRepository;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreamingSttResultService {

  private static final Logger log = LoggerFactory.getLogger(StreamingSttResultService.class);
  private static final String STREAMING_AUDIO_MIME_TYPE = "audio/L16;rate=16000;channels=1";
  private static final String DEFAULT_LOCALE = "ko-KR";

  private final CommunicationSessionRepository communicationSessionRepository;
  private final CommunicationMessageRepository communicationMessageRepository;

  public StreamingSttResultService(
      CommunicationSessionRepository communicationSessionRepository,
      CommunicationMessageRepository communicationMessageRepository) {
    this.communicationSessionRepository = communicationSessionRepository;
    this.communicationMessageRepository = communicationMessageRepository;
  }

  @Transactional
  public void saveFinalResult(Long userId, Long sessionId, String recognizedText, String locale) {
    if (userId == null || sessionId == null || recognizedText == null || recognizedText.isBlank()) {
      return;
    }

    communicationSessionRepository
        .findByIdAndUserIdAndStatusForUpdate(sessionId, userId, CommunicationSessionStatus.ACTIVE)
        .ifPresentOrElse(
            session -> saveMessage(session, recognizedText, resolveLocale(locale)),
            () ->
                log.warn(
                    "[Streaming STT] Active communication session not found. userId={}, sessionId={}",
                    userId,
                    sessionId));
  }

  private void saveMessage(CommunicationSession session, String recognizedText, String locale) {
    int messageOrder = session.allocateNextMessageOrder();
    CommunicationMessage message =
        CommunicationMessage.childSpeechToParentText(
            session,
            recognizedText.trim(),
            recognizedText.trim(),
            STREAMING_AUDIO_MIME_TYPE,
            locale,
            messageOrder);

    communicationMessageRepository.save(message);
  }

  private String resolveLocale(String locale) {
    return locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;
  }
}
