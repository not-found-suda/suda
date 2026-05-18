package com.ssafy.backend.domain.learn.entity;

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
@Table(name = "learn")
public class Learn extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private LearnCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LearnDifficulty difficulty;

  @Column(nullable = false, length = 100)
  private String word;

  @Column(nullable = false, name = "display_text", length = 100)
  private String displayText;

  @Column(name = "pronunciation_text", length = 100)
  private String pronunciationText;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "audio_url", length = 500)
  private String audioUrl;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  protected Learn() {}

  public Long getId() {
    return id;
  }

  public LearnCategory getCategory() {
    return category;
  }

  public LearnDifficulty getDifficulty() {
    return difficulty;
  }

  public String getWord() {
    return word;
  }

  public String getDisplayText() {
    return displayText;
  }

  public String getPronunciationText() {
    return pronunciationText;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getAudioUrl() {
    return audioUrl;
  }

  public boolean isActive() {
    return active;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }
}
