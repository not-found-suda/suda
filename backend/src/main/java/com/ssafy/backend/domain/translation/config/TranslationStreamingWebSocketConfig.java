package com.ssafy.backend.domain.translation.config;

import com.ssafy.backend.domain.translation.websocket.TranslationStreamingSttWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TranslationStreamingWebSocketConfig implements WebSocketConfigurer {

  private final TranslationStreamingSttWebSocketHandler streamingSttWebSocketHandler;

  public TranslationStreamingWebSocketConfig(
      TranslationStreamingSttWebSocketHandler streamingSttWebSocketHandler) {
    this.streamingSttWebSocketHandler = streamingSttWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(streamingSttWebSocketHandler, "/ws/v1/translation/speech-to-text/stream")
        .setAllowedOriginPatterns("*");
  }
}
