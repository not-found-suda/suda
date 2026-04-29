package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.domain.comms.dto.ChildSpeechCorrectionResult;
import org.springframework.stereotype.Component;

@Component
public class ChildSpeechCorrectionClient {

  private final OpenAiClient openAiClient;

  public ChildSpeechCorrectionClient(OpenAiClient openAiClient) {
    this.openAiClient = openAiClient;
  }

  public ChildSpeechCorrectionResult correct(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      return new ChildSpeechCorrectionResult("", "");
    }

    String systemInstruction =
        """
      너는 0~5세 아이의 부정확한 발화를 자연스러운 한국어로 보정하는 역할이다.

      규칙:
      - 설명 없이 보정된 문장만 출력한다.
      - 존댓말로 바꾸지 마라.
      - 원래 의미를 과하게 바꾸지 않는다.
      - 아이가 말한 것처럼 짧고 자연스럽게 보정한다.
      - 감탄문이면 자연스럽게 느낌표를 붙여도 된다.
      - 확실하지 않으면 입력 문장을 최대한 유지한다.

      예시:
      옴마 -> 엄마!
      압빠 -> 아빠!
      무 주 -> 물 줘!
      까까 조 -> 까까 줘!
      안아 조 -> 안아 줘!
      맘마 머거 -> 맘마 먹어!
      이거 모야 -> 이거 뭐야?
      """;

    String correctedText = openAiClient.generateText(systemInstruction, rawText).trim();

    return new ChildSpeechCorrectionResult(rawText, correctedText);
  }
}
