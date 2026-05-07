package com.ssafy.backend.domain.learn.entity;

public enum LearnDifficulty {
  EASY("쉬움"),
  MEDIUM("보통"),
  HARD("어려움");

  private final String displayName;

  LearnDifficulty(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
