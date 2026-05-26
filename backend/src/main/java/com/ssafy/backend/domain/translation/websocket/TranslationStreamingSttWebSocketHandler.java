package com.ssafy.backend.domain.translation.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.domain.comms.service.ClovaSpeechStreamingClient;
import com.ssafy.backend.domain.comms.service.ClovaSpeechStreamingClient.StreamingCall;
import com.ssafy.backend.domain.comms.service.StreamingSttResultService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class TranslationStreamingSttWebSocketHandler extends AbstractWebSocketHandler {

  private static final Logger log =
      LoggerFactory.getLogger(TranslationStreamingSttWebSocketHandler.class);

  private static final String SESSION_STATE_ATTRIBUTE = "translationStreamingSttState";
  private static final String DEFAULT_LOCALE = "ko-KR";
  private static final long END_RESPONSE_TIMEOUT_SECONDS = 30;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ClovaSpeechStreamingClient clovaSpeechStreamingClient;
  private final StreamingSttResultService streamingSttResultService;
  private final ScheduledExecutorService endTimeoutExecutor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "translation-stt-end-timeout");
            thread.setDaemon(true);
            return thread;
          });

  public TranslationStreamingSttWebSocketHandler(
      ClovaSpeechStreamingClient clovaSpeechStreamingClient,
      StreamingSttResultService streamingSttResultService) {
    this.clovaSpeechStreamingClient = clovaSpeechStreamingClient;
    this.streamingSttResultService = streamingSttResultService;
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode payload = objectMapper.readTree(message.getPayload());
    String type = payload.path("type").asText("");

    switch (type) {
      case "start" -> startStreaming(session, payload);
      case "end" -> endStreaming(session);
      default -> sendError(session, "Unsupported streaming STT message type: " + type);
    }
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message)
      throws Exception {
    StreamingSessionState state = getState(session);
    if (state == null || state.streamingCall() == null) {
      sendError(session, "Streaming STT session is not started.");
      return;
    }

    ByteBuffer payload = message.getPayload();
    byte[] audioBytes = new byte[payload.remaining()];
    payload.get(audioBytes);
    state.acceptAudio(audioBytes);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    StreamingSessionState state = removeState(session);
    if (state != null) {
      state.cancelEndTimeout();
      state.close();
    }
  }

  @PreDestroy
  void shutdownEndTimeoutExecutor() {
    endTimeoutExecutor.shutdownNow();
  }

  private void startStreaming(WebSocketSession session, JsonNode payload) throws IOException {
    if (getState(session) != null) {
      sendError(session, "Streaming STT session is already started.");
      return;
    }

    Long sessionId = readLongOrNull(payload, "sessionId");
    String locale = payload.path("locale").asText(DEFAULT_LOCALE);
    Long userId = resolveUserId(session);

    if (userId == null) {
      sendError(session, "Authentication is required for streaming STT.");
      return;
    }

    if (sessionId == null) {
      sendError(session, "sessionId is required for streaming STT.");
      return;
    }

    if (!streamingSttResultService.canSaveFinalResult(userId, sessionId)) {
      sendError(session, "Active communication session is not found.");
      return;
    }

    StreamingSessionState state = new StreamingSessionState(userId, sessionId, locale);
    StreamingCall streamingCall =
        clovaSpeechStreamingClient.openStream(
            new ClovaSpeechStreamingClient.ResponseHandler() {
              @Override
              public void onResponse(String contents) {
                handleClovaResponse(session, state, contents);
              }

              @Override
              public void onError(Throwable throwable) {
                handleClovaError(session, state, throwable);
              }

              @Override
              public void onCompleted() {
                handleClovaCompleted(session, state);
              }
            });

    state.bind(streamingCall);
    session.getAttributes().put(SESSION_STATE_ATTRIBUTE, state);
    sendJson(session, Map.of("type", "started", "locale", locale));
  }

  private void endStreaming(WebSocketSession session) throws IOException {
    StreamingSessionState state = getState(session);
    if (state == null) {
      sendError(session, "Streaming STT session is not started.");
      return;
    }

    state.complete();
    state.scheduleEndTimeout(
        endTimeoutExecutor.schedule(
            () -> handleEndTimeout(session, state),
            END_RESPONSE_TIMEOUT_SECONDS,
            TimeUnit.SECONDS));
  }

  private void handleClovaResponse(
      WebSocketSession session, StreamingSessionState state, String contents) {
    try {
      JsonNode root = objectMapper.readTree(contents);
      if (isConfigResponse(root)) {
        sendJson(session, Map.of("type", "config", "status", "success"));
        return;
      }

      JsonNode transcription = root.path("transcription");
      String text = transcription.path("text").asText("");
      if (text.isBlank()) {
        return;
      }

      double confidence =
          transcription.path("confidence").isNumber()
              ? transcription.path("confidence").asDouble()
              : Double.NaN;
      boolean isFinal = isTranscriptionResponse(root);

      if (isFinal) {
        state.appendRecognizedText(text);
      }

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("type", isFinal ? "final" : "partial");
      response.put("recognizedText", text);
      response.put("correctedText", text);
      response.put("corrected", false);
      if (!Double.isNaN(confidence)) {
        response.put("confidence", confidence);
      }
      response.put("locale", state.locale());

      sendJson(session, response);

    } catch (Exception e) {
      log.warn("[Streaming STT] Failed to handle CLOVA Speech response. contents={}", contents, e);
      handleClovaError(session, state, e);
    }
  }

  private void handleClovaError(
      WebSocketSession session, StreamingSessionState state, Throwable throwable) {
    state.cancelEndTimeout();

    if (!removeStateIfSame(session, state)) {
      state.close();
      return;
    }

    log.warn("[Streaming STT] CLOVA Speech stream failed.", throwable);

    try {
      sendError(session, "CLOVA Speech streaming failed.");
    } catch (IOException e) {
      log.warn("[Streaming STT] Failed to send error message to websocket client.", e);
    } finally {
      state.close();
    }
  }

  private void handleClovaCompleted(WebSocketSession session, StreamingSessionState state) {
    state.cancelEndTimeout();
    if (!removeStateIfSame(session, state)) {
      return;
    }

    if (state.markSaved()) {
      streamingSttResultService.saveFinalResult(
          state.userId(), state.sessionId(), state.recognizedText(), state.locale());
    }

    try {
      sendJson(session, Map.of("type", "closed"));
    } catch (IOException e) {
      log.warn("[Streaming STT] Failed to send close message to websocket client.", e);
    }
  }

  private void handleEndTimeout(WebSocketSession session, StreamingSessionState state) {
    if (!removeStateIfSame(session, state)) {
      return;
    }

    state.close();

    try {
      sendError(session, "CLOVA Speech streaming response timed out.");
    } catch (IOException e) {
      log.warn("[Streaming STT] Failed to send timeout message to websocket client.", e);
    }
  }

  private boolean isConfigResponse(JsonNode root) {
    return hasResponseType(root, "config");
  }

  private boolean isTranscriptionResponse(JsonNode root) {
    return hasResponseType(root, "transcription") || root.has("transcription");
  }

  private boolean hasResponseType(JsonNode root, String expectedType) {
    JsonNode responseTypes = root.path("responseType");
    if (!responseTypes.isArray()) {
      return false;
    }

    for (JsonNode responseType : responseTypes) {
      if (expectedType.equals(responseType.asText())) {
        return true;
      }
    }
    return false;
  }

  private Long readLongOrNull(JsonNode payload, String fieldName) {
    JsonNode value = payload.path(fieldName);
    return value.isIntegralNumber() ? value.asLong() : null;
  }

  private Long resolveUserId(WebSocketSession session) {
    Principal principal = session.getPrincipal();
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof Long userId) {
      return userId;
    }
    return null;
  }

  private StreamingSessionState getState(WebSocketSession session) {
    Object value = session.getAttributes().get(SESSION_STATE_ATTRIBUTE);
    return value instanceof StreamingSessionState state ? state : null;
  }

  private StreamingSessionState removeState(WebSocketSession session) {
    Object value = session.getAttributes().remove(SESSION_STATE_ATTRIBUTE);
    return value instanceof StreamingSessionState state ? state : null;
  }

  private boolean removeStateIfSame(WebSocketSession session, StreamingSessionState expectedState) {
    synchronized (session) {
      if (getState(session) != expectedState) {
        return false;
      }
      session.getAttributes().remove(SESSION_STATE_ATTRIBUTE);
      return true;
    }
  }

  private void sendError(WebSocketSession session, String message) throws IOException {
    sendJson(session, Map.of("type", "error", "message", message));
  }

  private void sendJson(WebSocketSession session, Map<String, ?> payload) throws IOException {
    synchronized (session) {
      if (session.isOpen()) {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
      }
    }
  }

  private static final class StreamingSessionState {
    private final Long userId;
    private final Long sessionId;
    private final String locale;
    private final StringBuilder recognizedText = new StringBuilder();
    private final AtomicBoolean saved = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private StreamingCall streamingCall;
    private byte[] pendingAudio;
    private ScheduledFuture<?> endTimeout;

    private StreamingSessionState(Long userId, Long sessionId, String locale) {
      this.userId = userId;
      this.sessionId = sessionId;
      this.locale = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;
    }

    private void bind(StreamingCall streamingCall) {
      this.streamingCall = streamingCall;
    }

    private StreamingCall streamingCall() {
      return streamingCall;
    }

    private void acceptAudio(byte[] audioBytes) {
      if (audioBytes == null || audioBytes.length == 0 || streamingCall == null) {
        return;
      }

      if (pendingAudio != null) {
        streamingCall.sendAudio(pendingAudio, false);
      }
      pendingAudio = audioBytes;
    }

    private Long userId() {
      return userId;
    }

    private Long sessionId() {
      return sessionId;
    }

    private String locale() {
      return locale;
    }

    private void appendRecognizedText(String text) {
      if (text == null || text.isBlank()) {
        return;
      }

      String normalizedText = text.trim();
      if (!recognizedText.isEmpty()
          && !Character.isWhitespace(recognizedText.charAt(recognizedText.length() - 1))
          && !Character.isWhitespace(normalizedText.charAt(0))) {
        recognizedText.append(' ');
      }
      recognizedText.append(normalizedText);
    }

    private String recognizedText() {
      return recognizedText.toString().trim();
    }

    private boolean markSaved() {
      return saved.compareAndSet(false, true);
    }

    private void complete() {
      if (streamingCall != null && completed.compareAndSet(false, true)) {
        if (pendingAudio != null) {
          streamingCall.sendAudio(pendingAudio, true);
          pendingAudio = null;
        }
        streamingCall.complete();
      }
    }

    private void scheduleEndTimeout(ScheduledFuture<?> endTimeout) {
      this.endTimeout = endTimeout;
    }

    private void cancelEndTimeout() {
      if (endTimeout != null) {
        endTimeout.cancel(false);
      }
    }

    private void close() {
      if (streamingCall != null) {
        streamingCall.close();
      }
    }
  }
}
