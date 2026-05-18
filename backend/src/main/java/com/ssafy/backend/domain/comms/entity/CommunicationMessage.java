package com.ssafy.backend.domain.comms.entity;

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

@Entity
@Table(name = "communication_messages")
public class CommunicationMessage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private CommunicationSession session;

  @Enumerated(EnumType.STRING)
  @Column(name = "speaker_role", nullable = false, length = 20)
  private SpeakerRole speakerRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CommunicationDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private CommunicationSourceType sourceType;

  @Column(name = "original_words", columnDefinition = "TEXT")
  private String originalWords;

  @Column(name = "recognized_text", columnDefinition = "TEXT")
  private String recognizedText;

  @Column(name = "final_text", nullable = false, columnDefinition = "TEXT")
  private String finalText;

  @Column(name = "audio_mime_type", length = 100)
  private String audioMimeType;

  @Column(name = "tts_speaker", length = 50)
  private String ttsSpeaker;

  @Column(nullable = false, length = 20)
  private String locale;

  @Column(name = "message_order", nullable = false)
  private int messageOrder;

  protected CommunicationMessage() {}

  private CommunicationMessage(
      CommunicationSession session,
      SpeakerRole speakerRole,
      CommunicationDirection direction,
      CommunicationSourceType sourceType,
      String originalWords,
      String recognizedText,
      String finalText,
      String audioMimeType,
      String ttsSpeaker,
      String locale,
      int messageOrder) {
    this.session = session;
    this.speakerRole = speakerRole;
    this.direction = direction;
    this.sourceType = sourceType;
    this.originalWords = originalWords;
    this.recognizedText = recognizedText;
    this.finalText = finalText;
    this.audioMimeType = audioMimeType;
    this.ttsSpeaker = ttsSpeaker;
    this.locale = locale;
    this.messageOrder = messageOrder;
  }

  public static CommunicationMessage parentSignToChildSpeech(
      CommunicationSession session,
      String originalWords,
      String finalText,
      String audioMimeType,
      String ttsSpeaker,
      String locale,
      int messageOrder) {
    return new CommunicationMessage(
        session,
        SpeakerRole.PARENT,
        CommunicationDirection.PARENT_SIGN_TO_CHILD_SPEECH,
        CommunicationSourceType.SIGN_WORDS,
        originalWords,
        null,
        finalText,
        audioMimeType,
        ttsSpeaker,
        locale,
        messageOrder);
  }

  public static CommunicationMessage childSpeechToParentText(
      CommunicationSession session,
      String recognizedText,
      String finalText,
      String audioMimeType,
      String locale,
      int messageOrder) {
    return new CommunicationMessage(
        session,
        SpeakerRole.CHILD,
        CommunicationDirection.CHILD_SPEECH_TO_PARENT_TEXT,
        CommunicationSourceType.AUDIO,
        null,
        recognizedText,
        finalText,
        audioMimeType,
        null,
        locale,
        messageOrder);
  }

  public Long getId() {
    return id;
  }

  public CommunicationSession getSession() {
    return session;
  }

  public SpeakerRole getSpeakerRole() {
    return speakerRole;
  }

  public CommunicationDirection getDirection() {
    return direction;
  }

  public CommunicationSourceType getSourceType() {
    return sourceType;
  }

  public String getOriginalWords() {
    return originalWords;
  }

  public String getRecognizedText() {
    return recognizedText;
  }

  public String getFinalText() {
    return finalText;
  }

  public String getAudioMimeType() {
    return audioMimeType;
  }

  public String getTtsSpeaker() {
    return ttsSpeaker;
  }

  public String getLocale() {
    return locale;
  }

  public int getMessageOrder() {
    return messageOrder;
  }
}
