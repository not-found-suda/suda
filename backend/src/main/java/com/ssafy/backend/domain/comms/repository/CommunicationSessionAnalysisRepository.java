package com.ssafy.backend.domain.comms.repository;

import com.ssafy.backend.domain.comms.entity.CommunicationAnalysisStatus;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionAnalysis;
import com.ssafy.backend.domain.report.repository.ReportCommunicationAnalysisQueryRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
      and a.analysisStatus = :analysisStatus
    order by s.startedAt desc
    """)
  List<ReportCommunicationAnalysisQueryRow> findCommunicationAnalysisRows(
      @Param("childId") Long childId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("analysisStatus") CommunicationAnalysisStatus analysisStatus);
}
