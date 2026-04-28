package com.ssafy.backend.domain.translation.service;

import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TranslationServiceImpl implements TranslationService {

  private static final String MOCK_AUDIO_BASE64 = "BASE64_ENCODED_AUDIO";
  private static final String MOCK_AUDIO_MIME_TYPE = "audio/mpeg";

  @Override
  public SignToSpeechResponseDto translateSignToSpeech(SignToSpeechRequestDto requestDto) {
    List<String> words = requestDto.words();
    String correctedText = String.join(" ", words);
    boolean requestTts = requestDto.requestTts() == null || requestDto.requestTts();

    return new SignToSpeechResponseDto(
        words,
        correctedText,
        requestTts ? MOCK_AUDIO_BASE64 : null,
        requestTts ? MOCK_AUDIO_MIME_TYPE : null,
        false);
  }
}
