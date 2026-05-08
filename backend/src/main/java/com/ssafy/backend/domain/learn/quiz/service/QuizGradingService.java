package com.ssafy.backend.domain.learn.quiz.service;

import org.springframework.stereotype.Service;

@Service
public class QuizGradingService {

  public QuizGrade grade(String targetText, String recognizedText) {
    String target = normalize(targetText);
    String recognized = normalize(recognizedText);

    if (target.isBlank() || recognized.isBlank()) {
      return new QuizGrade(false, 1, "다시 한번 연습해봐요!", "인식된 텍스트 없음", 0.0);
    }

    if (target.equals(recognized)) {
      return new QuizGrade(true, 3, "정확하게 말했어요!", "완전 일치", 1.0);
    }

    double similarity = calculateSimilarity(target, recognized);

    if (similarity >= 0.85) {
      return new QuizGrade(true, 3, "정확하게 말했어요!", "높은 유사도", similarity);
    }

    if (similarity >= 0.5) {
      return new QuizGrade(true, 2, "조금 아쉽지만 잘했어요!", "부분 유사", similarity);
    }

    return new QuizGrade(false, 1, "다시 한번 연습해봐요!", "낮은 유사도", similarity);
  }

  private String normalize(String text) {
    if (text == null) {
      return "";
    }

    return text.trim().replaceAll("\\s+", "").replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
  }

  private double calculateSimilarity(String source, String target) {
    int maxLength = Math.max(source.length(), target.length());

    if (maxLength == 0) {
      return 1.0;
    }

    int distance = levenshteinDistance(source, target);
    return 1.0 - ((double) distance / maxLength);
  }

  private int levenshteinDistance(String source, String target) {
    int sourceLength = source.length();
    int targetLength = target.length();

    int[][] dp = new int[sourceLength + 1][targetLength + 1];

    for (int i = 0; i <= sourceLength; i++) {
      dp[i][0] = i;
    }

    for (int j = 0; j <= targetLength; j++) {
      dp[0][j] = j;
    }

    for (int i = 1; i <= sourceLength; i++) {
      for (int j = 1; j <= targetLength; j++) {
        int cost = source.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;

        dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
      }
    }

    return dp[sourceLength][targetLength];
  }
}
