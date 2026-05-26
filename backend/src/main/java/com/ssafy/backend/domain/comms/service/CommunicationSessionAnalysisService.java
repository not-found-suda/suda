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
  private static final String ANALYSIS_VERSION = "v2";
  private static final String PROMPT_VERSION = "v2";

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
        analysis.empty(ANALYSIS_VERSION, PROMPT_VERSION);
        return;
      }

      String userInput = buildUserInput(childTexts);
      String summaryJson = openAiClient.generateJson(systemInstruction(), userInput);

      objectMapper.readTree(summaryJson);

      analysis.complete(summaryJson, MODEL_NAME, ANALYSIS_VERSION, PROMPT_VERSION);

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
        당신은 만 0~5세 아동의 발화 기록을 보호자에게 설명하는 언어 발달 리포트 도우미입니다.

        반드시 아래 JSON 형식으로만 응답하세요.
        설명 문장, 마크다운, 코드블록은 절대 포함하지 마세요.
        의학적 진단, 발달 지연 확정 표현, 치료 필요 표현은 사용하지 마세요.
        "발달 지연", "장애", "치료가 필요합니다", "또래보다 명확히 부족합니다" 같은 단정 표현은 금지합니다.
        "오늘 기록만 보면", "이러한 양상이 반복된다면", "보호자가 걱정된다면" 같은 완곡한 표현을 사용하세요.
        보호자가 바로 실천할 수 있는 구체적인 대화 가이드를 포함하세요.

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
            "QUESTION": 0,
            "OTHER": 0
          },
          "communicationLevel": "LOW",
          "vocabularyDiversityLevel": "LOW",
          "sentenceExpansionLevel": "LOW",
          "strengths": ["string"],
          "improvementPoints": ["string"],
          "parentGuide": ["string"],
          "recommendedActivities": ["string"],
          "developmentReference": "string",
          "cautionLevel": "NONE",
          "consultationGuide": "string",
          "summary": "string"
        }

        분석 기준:
        - utteranceCount는 입력된 아이 발화 문장 수입니다.
        - averageSentenceLength는 문장별 평균 단어 수입니다.
        - frequentWords는 자주 등장한 단어 상위 5개입니다.
        - utteranceWords는 주요 단어 목록입니다.
        - expressionTypeCounts는 발화 의도를 요구/감정/응답/놀이/질문/기타로 분류한 개수입니다.
        - communicationLevel은 발화 수와 표현 다양성을 기준으로 LOW/NORMAL/HIGH 중 하나로 작성하세요.
        - vocabularyDiversityLevel은 사용 단어 종류와 반복 정도를 기준으로 LOW/NORMAL/HIGH 중 하나로 작성하세요.
        - sentenceExpansionLevel은 1단어 발화가 많은지, 2~3단어 이상 문장이 있는지를 기준으로 LOW/NORMAL/HIGH 중 하나로 작성하세요.
        - strengths는 아이가 잘하고 있는 점을 보호자 관점에서 1~3개 작성하세요.
        - improvementPoints는 보완하면 좋은 점을 1~3개 작성하세요.
        - parentGuide는 보호자가 집에서 바로 실천할 수 있는 대화 방법을 2~4개 작성하세요.
        - recommendedActivities는 학습/놀이 추천을 1~3개 작성하세요.
        - developmentReference는 현재 발화 기록을 바탕으로 언어 표현 양상을 설명하는 참고 문장입니다.
        - cautionLevel은 NONE, WATCH, CONSULT 중 하나입니다.
          - NONE: 특별한 우려 표현 없이 긍정적 가이드 제공
          - WATCH: 같은 양상이 반복되는지 관찰하고 가정 내 연습 권장
          - CONSULT: 보호자가 걱정되거나 같은 양상이 지속되면 전문가 상담 권장
        - consultationGuide는 진단이 아니라 상담 권장 문구로 작성하세요.
        - summary는 보호자가 이해하기 쉬운 한국어 1~2문장으로 작성하세요.
        """;
  }
}
