package com.ssafy.backend.domain.comms.repository;

import com.ssafy.backend.domain.comms.entity.CommunicationMessage;
import com.ssafy.backend.domain.comms.entity.SpeakerRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, Long> {

  List<CommunicationMessage> findBySessionIdOrderByMessageOrderAsc(Long sessionId);

  List<CommunicationMessage> findBySessionIdAndSpeakerRoleOrderByMessageOrderAsc(
      Long sessionId, SpeakerRole speakerRole);

  int countBySessionIdAndSpeakerRole(Long sessionId, SpeakerRole speakerRole);

  int countBySessionId(Long sessionId);
}
