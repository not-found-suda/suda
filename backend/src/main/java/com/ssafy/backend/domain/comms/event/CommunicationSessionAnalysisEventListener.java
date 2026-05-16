package com.ssafy.backend.domain.comms.event;

import com.ssafy.backend.domain.comms.service.CommunicationSessionAnalysisService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommunicationSessionAnalysisEventListener {

  private final CommunicationSessionAnalysisService analysisService;

  public CommunicationSessionAnalysisEventListener(
      CommunicationSessionAnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleCommunicationSessionEnded(CommunicationSessionEndedEvent event) {
    analysisService.analyzeSession(event.sessionId());
  }
}
