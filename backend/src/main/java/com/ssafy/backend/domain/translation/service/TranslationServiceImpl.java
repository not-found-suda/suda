package com.ssafy.backend.domain.translation.service;

import com.ssafy.backend.domain.comms.dto.ChildSpeechCorrectionResult;
import com.ssafy.backend.domain.comms.service.ChildSpeechCorrectionClient;
import com.ssafy.backend.domain.comms.service.ClovaSttClient;
import com.ssafy.backend.domain.comms.service.ClovaTtsClient;
import com.ssafy.backend.domain.comms.service.SignLanguageCorrectionClient;
import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import com.ssafy.backend.domain.translation.exception.TranslationErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranslationServiceImpl implements TranslationService {

  private static final String DEFAULT_LOCALE = "ko-KR";

  private static final Set<String> SUPPORTED_AUDIO_MIME_TYPES =
      Set.of(
          "audio/mpeg",
          "audio/mp3",
          "audio/mp4",
          "audio/wav",
          "audio/wave",
          "audio/x-wav",
          "audio/webm");

  private final SignLanguageCorrectionClient signLanguageCorrectionClient;
  private final ClovaTtsClient clovaTtsClient;
  private final ClovaSttClient clovaSttClient;
  private final ChildSpeechCorrectionClient childSpeechCorrectionClient;

  public TranslationServiceImpl(
      SignLanguageCorrectionClient signLanguageCorrectionClient,
      ClovaTtsClient clovaTtsClient,
      ClovaSttClient clovaSttClient,
      ChildSpeechCorrectionClient childSpeechCorrectionClient) {
    this.signLanguageCorrectionClient = signLanguageCorrectionClient;
    this.clovaTtsClient = clovaTtsClient;
    this.clovaSttClient = clovaSttClient;
    this.childSpeechCorrectionClient = childSpeechCorrectionClient;
  }

  @Override
  public SignToSpeechResponseDto translateSignToSpeech(SignToSpeechRequestDto requestDto) {
    List<String> words = requestDto.words();

    String signText = words.toString();

    String correctedText;
    try {
      correctedText = signLanguageCorrectionClient.correct(signText);
    } catch (Exception e) {
      throw new BusinessException(TranslationErrorCode.SIGN_CORRECTION_FAILED);
    }

    boolean requestTts = requestDto.requestTts() == null || requestDto.requestTts();

    String audioBase64 = null;
    String audioMimeType = null;

    if (requestTts) {
      try {
        byte[] audioBytes = clovaTtsClient.synthesize(correctedText);
        audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
        audioMimeType = clovaTtsClient.getAudioMimeType();
      } catch (Exception e) {
        throw new BusinessException(TranslationErrorCode.TEXT_TO_SPEECH_FAILED);
      }
    }

    boolean corrected = !String.join(" ", words).equals(correctedText);

    return new SignToSpeechResponseDto(words, correctedText, audioBase64, audioMimeType, corrected);
  }

  @Override
  public SpeechToTextResponseDto translateSpeechToText(
      MultipartFile audioFile, String locale, String audioMimeType) {

    validateAudioFile(audioFile);

    String resolvedAudioMimeType = resolveAudioMimeType(audioFile, audioMimeType);
    String resolvedLocale = resolveLocale(locale);

    String recognizedText;
    try {
      recognizedText = clovaSttClient.transcribe(audioFile, resolvedLocale, resolvedAudioMimeType);
    } catch (Exception e) {
      throw new BusinessException(TranslationErrorCode.SPEECH_RECOGNITION_FAILED);
    }

    if (recognizedText == null || recognizedText.isBlank()) {
      throw new BusinessException(TranslationErrorCode.UNRECOGNIZABLE_AUDIO);
    }

    ChildSpeechCorrectionResult correctionResult;
    try {
      correctionResult = childSpeechCorrectionClient.correct(recognizedText);
    } catch (Exception e) {
      throw new BusinessException(TranslationErrorCode.CHILD_SPEECH_CORRECTION_FAILED);
    }

    String correctedText = correctionResult.correctedText();
    boolean corrected = !recognizedText.trim().equals(correctedText.trim());

    return new SpeechToTextResponseDto(
        recognizedText, correctedText, corrected, null, resolvedLocale);
  }

  private void validateAudioFile(MultipartFile audioFile) {
    if (audioFile == null || audioFile.isEmpty()) {
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }
  }

  private String resolveAudioMimeType(MultipartFile audioFile, String audioMimeType) {
    String resolvedAudioMimeType =
        audioMimeType != null && !audioMimeType.isBlank()
            ? audioMimeType
            : audioFile.getContentType();

    String normalizedAudioMimeType =
        resolvedAudioMimeType == null
            ? null
            : resolvedAudioMimeType.trim().toLowerCase(Locale.ROOT);

    if (normalizedAudioMimeType == null
        || !SUPPORTED_AUDIO_MIME_TYPES.contains(normalizedAudioMimeType)) {
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }

    return normalizedAudioMimeType;
  }

  private String resolveLocale(String locale) {
    String resolvedLocale = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;

    if (!DEFAULT_LOCALE.equals(resolvedLocale)) {
      throw new BusinessException(TranslationErrorCode.INVALID_LOCALE);
    }

    return resolvedLocale;
  }
}
