package com.ssafy.backend.domain.comms.repository;

import com.ssafy.backend.domain.comms.entity.CommunicationSessionAnalysis;
import com.ssafy.backend.domain.report.repository.ReportCommunicationAnalysisQueryRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommunicationSessionAnalysisRepository
    extends JpaRepository<CommunicationSessionAnalysis, Long> {

  Optional<CommunicationSessionAnalysis> findBySessionId(Long sessionId);

  boolean existsBySessionId(Long sessionId);

  @Query(
      """
    select new com.ssafy.backend.domain.report.repository.ReportCommunicationAnalysisQueryRow(
      s.id,
      s.startedAt,
      s.endedAt,
      s.messageCount,
      a.analysisStatus,
      a.summaryJson,
      a.analyzedAt
    )
    from CommunicationSessionAnalysis a
    join a.session s
    where s.childProfile.id = :childId
      and s.startedAt >= :from
      and s.startedAt < :to
    order by s.startedAt desc
    """)
  List<ReportCommunicationAnalysisQueryRow> findCommunicationAnalysisRows(
      Long childId, LocalDateTime from, LocalDateTime to);
}
