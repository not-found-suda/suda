package com.ssafy.backend.domain.comms.entity;

import com.ssafy.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "communication_session_analysis")
public class CommunicationSessionAnalysis extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false, unique = true)
  private CommunicationSession session;

  @Enumerated(EnumType.STRING)
  @Column(name = "analysis_status", nullable = false, length = 20)
  private CommunicationAnalysisStatus analysisStatus;

  @Column(name = "summary_json", columnDefinition = "jsonb")
  private String summaryJson;

  @Column(name = "analyzed_at")
  private LocalDateTime analyzedAt;

  @Column(name = "analysis_version", nullable = false, length = 30)
  private String analysisVersion;

  @Column(name = "model_name", length = 100)
  private String modelName;

  @Column(name = "prompt_version", nullable = false, length = 30)
  private String promptVersion;

  @Column(name = "analysis_error_code", length = 100)
  private String analysisErrorCode;

  protected CommunicationSessionAnalysis() {}

  private CommunicationSessionAnalysis(CommunicationSession session) {
    this.session = session;
    this.analysisStatus = CommunicationAnalysisStatus.PENDING;
    this.analysisVersion = "v1";
    this.promptVersion = "v1";
  }

  public static CommunicationSessionAnalysis pending(CommunicationSession session) {
    return new CommunicationSessionAnalysis(session);
  }

  public void markProcessing() {
    this.analysisStatus = CommunicationAnalysisStatus.PROCESSING;
    this.analysisErrorCode = null;
  }

  public void complete(
      String summaryJson, String modelName, String analysisVersion, String promptVersion) {
    this.analysisStatus = CommunicationAnalysisStatus.COMPLETED;
    this.summaryJson = summaryJson;
    this.modelName = modelName;
    this.analysisVersion = analysisVersion;
    this.promptVersion = promptVersion;
    this.analyzedAt = LocalDateTime.now();
    this.analysisErrorCode = null;
  }

  public void empty(String analysisVersion, String promptVersion) {
    this.analysisStatus = CommunicationAnalysisStatus.EMPTY;
    this.summaryJson = null;
    this.analysisVersion = analysisVersion;
    this.promptVersion = promptVersion;
    this.analyzedAt = LocalDateTime.now();
    this.analysisErrorCode = null;
  }

  public void fail(String errorCode) {
    this.analysisStatus = CommunicationAnalysisStatus.FAILED;
    this.analyzedAt = LocalDateTime.now();
    this.analysisErrorCode = errorCode;
  }

  public Long getId() {
    return id;
  }

  public CommunicationSession getSession() {
    return session;
  }

  public CommunicationAnalysisStatus getAnalysisStatus() {
    return analysisStatus;
  }

  public String getSummaryJson() {
    return summaryJson;
  }

  public LocalDateTime getAnalyzedAt() {
    return analyzedAt;
  }

  public String getAnalysisVersion() {
    return analysisVersion;
  }

  public String getModelName() {
    return modelName;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getAnalysisErrorCode() {
    return analysisErrorCode;
  }
}
