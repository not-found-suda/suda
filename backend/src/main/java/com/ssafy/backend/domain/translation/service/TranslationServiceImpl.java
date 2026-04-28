package com.ssafy.backend.domain.translation.service;

import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import com.ssafy.backend.domain.translation.exception.TranslationErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranslationServiceImpl implements TranslationService {

  private static final String MOCK_AUDIO_BASE64 = "BASE64_ENCODED_AUDIO";
  private static final String MOCK_AUDIO_MIME_TYPE = "audio/mpeg";
  private static final String DEFAULT_LOCALE = "ko-KR";
  private static final List<String> SUPPORTED_AUDIO_MIME_TYPES =
      List.of(
          "audio/mpeg",
          "audio/mp3",
          "audio/mp4",
          "audio/wav",
          "audio/wave",
          "audio/x-wav",
          "audio/webm");

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

  @Override
  public SpeechToTextResponseDto translateSpeechToText(
      MultipartFile audioFile, String locale, String audioMimeType) {
    if (audioFile == null || audioFile.isEmpty()) {
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }

    String resolvedAudioMimeType =
        audioMimeType != null && !audioMimeType.isBlank()
            ? audioMimeType
            : audioFile.getContentType();
    if (resolvedAudioMimeType == null
        || !SUPPORTED_AUDIO_MIME_TYPES.contains(resolvedAudioMimeType)) {
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }

    String resolvedLocale = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;
    if (!DEFAULT_LOCALE.equals(resolvedLocale)) {
      throw new BusinessException(TranslationErrorCode.INVALID_LOCALE);
    }

    return new SpeechToTextResponseDto("옴마", "엄마!", true, 0.91, resolvedLocale);
  }
}
