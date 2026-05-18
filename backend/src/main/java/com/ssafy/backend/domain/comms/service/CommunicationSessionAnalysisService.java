package com.ssafy.backend.domain.comms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.domain.comms.entity.CommunicationMessage;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionAnalysis;
import com.ssafy.backend.domain.comms.entity.SpeakerRole;
import com.ssafy.backend.domain.comms.repository.CommunicationMessageRepository;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionAnalysisRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunicationSessionAnalysisService {

  private static final String MODEL_NAME = "gemini";
  private static final String ANALYSIS_FAILED = "COMMS_ANALYSIS_FAILED";

  private final CommunicationSessionAnalysisRepository analysisRepository;
  private final CommunicationMessageRepository messageRepository;
  private final OpenAiClient openAiClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public CommunicationSessionAnalysisService(
      CommunicationSessionAnalysisRepository analysisRepository,
      CommunicationMessageRepository messageRepository,
      OpenAiClient openAiClient) {
    this.analysisRepository = analysisRepository;
    this.messageRepository = messageRepository;
    this.openAiClient = openAiClient;
  }

  public void analyzeSession(Long sessionId) {
    CommunicationSessionAnalysis analysis =
        analysisRepository
            .findBySessionId(sessionId)
            .orElseThrow(() -> new IllegalStateException("분석 대상 세션이 없습니다. sessionId=" + sessionId));

    try {
      analysis.markProcessing();

      List<String> childTexts =
          messageRepository
              .findBySessionIdAndSpeakerRoleOrderByMessageOrderAsc(sessionId, SpeakerRole.CHILD)
              .stream()
              .map(CommunicationMessage::getFinalText)
              .filter(text -> text != null && !text.isBlank())
              .toList();

      if (childTexts.isEmpty()) {
        analysis.empty();
        return;
      }

      String userInput = buildUserInput(childTexts);
      String summaryJson = openAiClient.generateJson(systemInstruction(), userInput);

      objectMapper.readTree(summaryJson);

      analysis.complete(summaryJson, MODEL_NAME);

    } catch (Exception e) {
      analysis.fail(ANALYSIS_FAILED);
    }
  }

  private String buildUserInput(List<String> childTexts) {
    StringBuilder sb = new StringBuilder();
    sb.append("아래는 아이의 발화 목록입니다.\n");
    sb.append("각 문장을 분석해서 지정된 JSON 형식으로만 응답하세요.\n\n");

    for (int i = 0; i < childTexts.size(); i++) {
      sb.append(i + 1).append(". ").append(childTexts.get(i)).append("\n");
    }

    return sb.toString();
  }

  private String systemInstruction() {
    return """
        당신은 아동 발화 데이터를 분석하는 언어 분석 도우미입니다.

        반드시 아래 JSON 형식으로만 응답하세요.
        설명 문장, 마크다운, 코드블록은 절대 포함하지 마세요.

        {
          "utteranceCount": 0,
          "averageSentenceLength": 0.0,
          "frequentWords": [
            {
              "word": "string",
              "count": 0
            }
          ],
          "utteranceWords": ["string"],
          "expressionTypeCounts": {
            "REQUEST": 0,
            "EMOTION": 0,
            "RESPONSE": 0,
            "PLAY": 0,
            "OTHER": 0
          },
          "summary": "string"
        }

        분석 기준:
        - utteranceCount는 입력된 발화 문장 수입니다.
        - averageSentenceLength는 문장별 평균 단어 수입니다.
        - frequentWords는 자주 등장한 단어 상위 5개입니다.
        - utteranceWords는 주요 단어 목록입니다.
        - expressionTypeCounts는 발화 의도를 요구/감정/응답/놀이/기타로 분류한 개수입니다.
        - summary는 보호자가 이해하기 쉬운 한국어 한두 문장으로 작성하세요.
        """;
  }
}
