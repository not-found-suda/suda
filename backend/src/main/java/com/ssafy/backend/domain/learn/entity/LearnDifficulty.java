package com.ssafy.backend.domain.learn.entity;

public enum LearnDifficulty {
  EASY("쉬움", "0~2세 아이를 위한 쉬운 단어"),
  NORMAL("보통", "3~4세 아이를 위한 기본 단어"),
  HARD("어려움", "5세 아이를 위한 확장 단어");

  private final String displayName;
  private final String description;

  LearnDifficulty(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }
}
