package com.ssafy.backend.domain.comms.service;

import org.springframework.stereotype.Component;

@Component
public class SignLanguageCorrectionClient {

  private final OpenAiClient openAiClient;

  public SignLanguageCorrectionClient(OpenAiClient openAiClient) {
    this.openAiClient = openAiClient;
  }

  public String correct(String signText) {
    if (signText == null || signText.isBlank()) {
      return "";
    }

    String systemInstruction =
        """
      입력은 수어식 단어 배열이다.

      규칙:
      - 0~5세 아이에게 부모가 말하듯 자연스러운 한국어 구어체 문장 하나로 바꾼다.
      - 반드시 반말로 말한다.
      - 존댓말은 사용하지 않는다.
      - 입력 단어의 핵심 의미는 유지한다.
      - 조사, 어미, 시제, 어순은 자연스럽게 바꿔도 된다.
      - 입력에 없는 새 주제는 추가하지 않는다.
      - 설명 없이 문장 하나만 출력한다.

      예시:
      [오늘, 뭐, 먹다] -> 오늘 뭐 먹고 싶어?
      [무슨, 아이스크림, 먹다] -> 무슨 아이스크림 먹을래?
      [우리 아이, 배, 좋아하다] -> 우리 아이는 배 좋아해?
      [엄마, 같이, 가다] -> 엄마랑 같이 가자.
      [이리, 오다] -> 이리 와!
      """;

    try {
      return openAiClient.generateText(systemInstruction, signText).trim();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("수어 문맥 보정에 실패했습니다.", e);
    }
  }
}
