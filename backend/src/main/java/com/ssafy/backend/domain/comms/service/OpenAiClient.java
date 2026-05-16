package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.global.ai.AiProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

  private final RestClient restClient;
  private final AiProperties aiProperties;

  public OpenAiClient(RestClient restClient, AiProperties aiProperties) {
    this.restClient = restClient;
    this.aiProperties = aiProperties;
  }

  public String generateText(String systemInstruction, String userInput) {
    if (userInput == null || userInput.isBlank()) {
      return "";
    }

    try {
      String result = callGemini(systemInstruction, userInput, 0.1, 64);

      if (result == null || result.isBlank()) {
        throw new IllegalStateException("Gemini 응답 텍스트가 비어 있습니다.");
      }

      return clean(result);

    } catch (RestClientResponseException e) {
      log.warn(
          "Gemini request failed. status={}, responseHeaders={}, responseBody={}, exceptionClass={}, message={}",
          e.getStatusCode(),
          e.getResponseHeaders(),
          abbreviate(e.getResponseBodyAsString(), 1000),
          e.getClass().getName(),
          e.getMessage(),
          e);

      throw new IllegalStateException("Gemini 문장 보정 호출에 실패했습니다.", e);

    } catch (Exception e) {
      log.warn(
          "Gemini request failed. exceptionClass={}, message={}",
          e.getClass().getName(),
          e.getMessage(),
          e);

      throw new IllegalStateException("Gemini 문장 보정 호출에 실패했습니다.", e);
    }
  }

  public String generateJson(String systemInstruction, String userInput) {
    if (userInput == null || userInput.isBlank()) {
      return "{}";
    }

    try {
      String result = callGemini(systemInstruction, userInput, 0.2, 1024);

      if (result == null || result.isBlank()) {
        throw new IllegalStateException("Gemini JSON 응답이 비어 있습니다.");
      }

      return cleanJson(result);

    } catch (RestClientResponseException e) {
      log.warn(
          "Gemini analysis request failed. status={}, responseHeaders={}, responseBody={}, exceptionClass={}, message={}",
          e.getStatusCode(),
          e.getResponseHeaders(),
          abbreviate(e.getResponseBodyAsString(), 1000),
          e.getClass().getName(),
          e.getMessage(),
          e);

      throw new IllegalStateException("Gemini 발화 분석 호출에 실패했습니다.", e);

    } catch (Exception e) {
      log.warn(
          "Gemini analysis request failed. exceptionClass={}, message={}",
          e.getClass().getName(),
          e.getMessage(),
          e);

      throw new IllegalStateException("Gemini 발화 분석 호출에 실패했습니다.", e);
    }
  }

  private String callGemini(
      String systemInstruction, String userInput, double temperature, int maxOutputTokens) {
    AiProperties.Gemini gemini = aiProperties.gemini();

    if (gemini == null) {
      throw new IllegalStateException("Gemini 설정이 없습니다.");
    }

    if (gemini.apiKey() == null || gemini.apiKey().isBlank()) {
      throw new IllegalStateException("Gemini API key가 설정되지 않았습니다.");
    }

    String url = "%s/v1beta/models/%s:generateContent".formatted(gemini.baseUrl(), gemini.model());

    GeminiRequest request =
        new GeminiRequest(
            new GeminiContent(List.of(new GeminiPart(systemInstruction))),
            List.of(new GeminiContent(List.of(new GeminiPart(userInput)))),
            new GeminiGenerationConfig(temperature, maxOutputTokens, new GeminiThinkingConfig(0)));

    GeminiResponse response =
        restClient
            .post()
            .uri(url)
            .header("x-goog-api-key", gemini.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(GeminiResponse.class);

    if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
      throw new IllegalStateException("Gemini 응답이 비어 있습니다.");
    }

    GeminiContent content = response.candidates().get(0).content();

    if (content == null || content.parts() == null || content.parts().isEmpty()) {
      throw new IllegalStateException("Gemini 응답 텍스트가 비어 있습니다.");
    }

    return content.parts().get(0).text();
  }

  private String clean(String result) {
    return result.trim().replace("\"", "").replace("'", "").replace("`", "").split("\\n")[0].trim();
  }

  private String cleanJson(String result) {
    return result.trim().replace("```json", "").replace("```", "").trim();
  }

  private String abbreviate(String value, int maxLength) {
    if (value == null) {
      return null;
    }

    if (value.length() <= maxLength) {
      return value;
    }

    return value.substring(0, maxLength) + "...";
  }

  private record GeminiRequest(
      GeminiContent systemInstruction,
      List<GeminiContent> contents,
      GeminiGenerationConfig generationConfig) {}

  private record GeminiContent(List<GeminiPart> parts) {}

  private record GeminiPart(String text) {}

  private record GeminiGenerationConfig(
      Double temperature, Integer maxOutputTokens, GeminiThinkingConfig thinkingConfig) {}

  private record GeminiThinkingConfig(Integer thinkingBudget) {}

  private record GeminiResponse(List<GeminiCandidate> candidates) {}

  private record GeminiCandidate(GeminiContent content) {}
}
