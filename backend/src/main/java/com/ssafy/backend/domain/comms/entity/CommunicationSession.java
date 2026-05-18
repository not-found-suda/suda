package com.ssafy.backend.domain.comms.entity;

import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "communication_sessions")
public class CommunicationSession extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "child_profile_id")
  private ChildProfile childProfile;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CommunicationSessionStatus status;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  @Column(name = "message_count", nullable = false)
  private int messageCount;

  @Column(name = "summary_text", columnDefinition = "TEXT")
  private String summaryText;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  protected CommunicationSession() {}

  private CommunicationSession(User user, ChildProfile childProfile, LocalDateTime expiresAt) {
    this.user = user;
    this.childProfile = childProfile;
    this.status = CommunicationSessionStatus.ACTIVE;
    this.startedAt = LocalDateTime.now();
    this.messageCount = 0;
    this.expiresAt = expiresAt;
  }

  public static CommunicationSession start(User user, ChildProfile childProfile) {
    return new CommunicationSession(user, childProfile, LocalDateTime.now().plusMonths(6));
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public ChildProfile getChildProfile() {
    return childProfile;
  }

  public CommunicationSessionStatus getStatus() {
    return status;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getEndedAt() {
    return endedAt;
  }

  public int getMessageCount() {
    return messageCount;
  }

  public String getSummaryText() {
    return summaryText;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public boolean isActive() {
    return this.status == CommunicationSessionStatus.ACTIVE;
  }

  public int allocateNextMessageOrder() {
    this.messageCount++;
    return this.messageCount;
  }

  public void end() {
    this.status = CommunicationSessionStatus.ENDED;
    this.endedAt = LocalDateTime.now();
  }

  public void updateSummaryText(String summaryText) {
    this.summaryText = summaryText;
  }
}
