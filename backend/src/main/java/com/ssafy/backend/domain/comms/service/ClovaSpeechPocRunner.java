package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.domain.comms.config.ClovaSpeechProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai.clova-speech.poc", name = "enabled", havingValue = "true")
public class ClovaSpeechPocRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ClovaSpeechPocRunner.class);

  private final ClovaSpeechProperties properties;
  private final ClovaSpeechStreamingClient streamingClient;

  public ClovaSpeechPocRunner(
      ClovaSpeechProperties properties, ClovaSpeechStreamingClient streamingClient) {
    this.properties = properties;
    this.streamingClient = streamingClient;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Path audioPath = properties.poc().audioPath();
    if (audioPath == null || !Files.isRegularFile(audioPath)) {
      throw new IllegalStateException("CLOVA Speech PoC audio file is not configured or missing.");
    }

    log.info(
        "[CLOVA Speech PoC] start. endpoint={}, language={}, audioPath={}, skipWavHeader={}",
        properties.endpoint(),
        properties.language(),
        audioPath,
        properties.poc().skipWavHeader());

    try (InputStream audioStream = Files.newInputStream(audioPath)) {
      List<String> responses =
          streamingClient.transcribe(audioStream, properties.poc().skipWavHeader());
      log.info("[CLOVA Speech PoC] completed. responseCount={}", responses.size());
    }
  }
}
