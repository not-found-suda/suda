package com.ssafy.backend.domain.comms.repository;

import com.ssafy.backend.domain.comms.entity.CommunicationSession;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationSessionRepository extends JpaRepository<CommunicationSession, Long> {

  Optional<CommunicationSession> findByIdAndUserId(Long id, Long userId);

  Optional<CommunicationSession> findByIdAndUserIdAndStatus(
      Long id, Long userId, CommunicationSessionStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from CommunicationSession s
      where s.id = :id
        and s.user.id = :userId
        and s.status = :status
      """)
  Optional<CommunicationSession> findByIdAndUserIdAndStatusForUpdate(
      @Param("id") Long id,
      @Param("userId") Long userId,
      @Param("status") CommunicationSessionStatus status);

  List<CommunicationSession> findByUserIdAndChildProfileIdOrderByStartedAtDesc(
      Long userId, Long childProfileId);

  List<CommunicationSession> findByUserIdAndChildProfileIdAndStatusOrderByStartedAtDesc(
      Long userId, Long childProfileId, CommunicationSessionStatus status);
}
