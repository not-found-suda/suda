package com.ssafy.backend.domain.translation.service;

import com.ssafy.backend.domain.comms.entity.CommunicationMessage;
import com.ssafy.backend.domain.comms.entity.CommunicationSession;
import com.ssafy.backend.domain.comms.entity.CommunicationSessionStatus;
import com.ssafy.backend.domain.comms.exception.CommunicationSessionErrorCode;
import com.ssafy.backend.domain.comms.repository.CommunicationMessageRepository;
import com.ssafy.backend.domain.comms.repository.CommunicationSessionRepository;
import com.ssafy.backend.domain.comms.service.ClovaSttClient;
import com.ssafy.backend.domain.comms.service.ClovaTtsClient;
import com.ssafy.backend.domain.comms.service.SignLanguageCorrectionClient;
import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import com.ssafy.backend.domain.translation.exception.TranslationErrorCode;
import com.ssafy.backend.domain.user.entity.TtsSpeaker;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class TranslationServiceImpl implements TranslationService {

  private static final Logger log = LoggerFactory.getLogger(TranslationServiceImpl.class);

  private static final String DEFAULT_LOCALE = "ko-KR";

  private static final Set<String> SUPPORTED_AUDIO_MIME_TYPES =
      Set.of("audio/wav", "audio/wave", "audio/x-wav");

  private final SignLanguageCorrectionClient signLanguageCorrectionClient;
  private final ClovaTtsClient clovaTtsClient;
  private final ClovaSttClient clovaSttClient;
  private final UserRepository userRepository;
  private final CommunicationSessionRepository communicationSessionRepository;
  private final CommunicationMessageRepository communicationMessageRepository;

  public TranslationServiceImpl(
      SignLanguageCorrectionClient signLanguageCorrectionClient,
      ClovaTtsClient clovaTtsClient,
      ClovaSttClient clovaSttClient,
      UserRepository userRepository,
      CommunicationSessionRepository communicationSessionRepository,
      CommunicationMessageRepository communicationMessageRepository) {
    this.signLanguageCorrectionClient = signLanguageCorrectionClient;
    this.clovaTtsClient = clovaTtsClient;
    this.clovaSttClient = clovaSttClient;
    this.userRepository = userRepository;
    this.communicationSessionRepository = communicationSessionRepository;
    this.communicationMessageRepository = communicationMessageRepository;
  }

  @Override
  public SignToSpeechResponseDto translateSignToSpeech(
      Long userId, SignToSpeechRequestDto requestDto) {
    List<String> words = requestDto.words();

    String signText = words.toString();

    String correctedText;
    try {
      correctedText = signLanguageCorrectionClient.correct(signText);
    } catch (Exception e) {
      log.warn(
          "[SignCorrection] Failed. traceId={}, userId={}, wordCount={}, exceptionClass={}, message={}",
          getTraceId(),
          userId,
          words.size(),
          e.getClass().getName(),
          e.getMessage(),
          e);
      throw new BusinessException(TranslationErrorCode.SIGN_CORRECTION_FAILED);
    }

    boolean requestTts = requestDto.requestTts() == null || requestDto.requestTts();

    String audioBase64 = null;
    String audioMimeType = null;
    String speaker = null;

    if (requestTts) {
      speaker = resolveTtsSpeaker(userId);

      try {
        log.info(
            "[TTS] Request. traceId={}, userId={}, speaker={}, textLength={}",
            getTraceId(),
            userId,
            speaker,
            getTextLength(correctedText));

        byte[] audioBytes = clovaTtsClient.synthesize(correctedText, speaker);
        audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
        audioMimeType = clovaTtsClient.getAudioMimeType();

        log.info(
            "[TTS] Success. traceId={}, userId={}, speaker={}, audioMimeType={}, audioBytes={}",
            getTraceId(),
            userId,
            speaker,
            audioMimeType,
            audioBytes.length);

      } catch (Exception e) {
        log.warn(
            "[TTS] Clova text to speech failed. traceId={}, userId={}, speaker={}, textLength={}, exceptionClass={}, message={}",
            getTraceId(),
            userId,
            speaker,
            getTextLength(correctedText),
            e.getClass().getName(),
            e.getMessage(),
            e);
        throw new BusinessException(TranslationErrorCode.TEXT_TO_SPEECH_FAILED);
      }
    }

    saveParentMessageIfSessionExists(
        userId,
        requestDto.sessionId(),
        words,
        correctedText,
        audioMimeType,
        speaker,
        requestDto.locale());

    boolean corrected = !String.join(" ", words).equals(correctedText);

    return new SignToSpeechResponseDto(words, correctedText, audioBase64, audioMimeType, corrected);
  }

  @Override
  public SpeechToTextResponseDto translateSpeechToText(
      Long userId, Long sessionId, MultipartFile audioFile, String locale, String audioMimeType) {

    logSpeechToTextRequest(audioFile, locale, audioMimeType);

    validateAudioFile(audioFile);

    String resolvedAudioMimeType = resolveAudioMimeType(audioFile, audioMimeType);
    validateWavHeader(audioFile);

    String resolvedLocale = resolveLocale(locale);

    String recognizedText;
    try {
      recognizedText = clovaSttClient.transcribe(audioFile, resolvedLocale, resolvedAudioMimeType);
    } catch (Exception e) {
      log.warn(
          "[STT] Clova speech recognition failed. traceId={}, exceptionClass={}, message={}",
          getTraceId(),
          e.getClass().getName(),
          e.getMessage(),
          e);
      throw new BusinessException(TranslationErrorCode.SPEECH_RECOGNITION_FAILED);
    }

    if (recognizedText == null || recognizedText.isBlank()) {
      log.warn("[STT] Recognized text is empty. traceId={}", getTraceId());
      throw new BusinessException(TranslationErrorCode.UNRECOGNIZABLE_AUDIO);
    }

    saveChildMessageIfSessionExists(
        userId, sessionId, recognizedText, resolvedAudioMimeType, resolvedLocale);

    return new SpeechToTextResponseDto(recognizedText, recognizedText, false, null, resolvedLocale);
  }

  private void saveParentMessageIfSessionExists(
      Long userId,
      Long sessionId,
      List<String> words,
      String finalText,
      String audioMimeType,
      String speaker,
      String locale) {
    if (sessionId == null) {
      return;
    }

    CommunicationSession session = getActiveSessionForUpdate(userId, sessionId);
    int messageOrder = session.allocateNextMessageOrder();

    CommunicationMessage message =
        CommunicationMessage.parentSignToChildSpeech(
            session,
            String.join(" ", words),
            finalText,
            audioMimeType,
            speaker,
            resolveLocale(locale),
            messageOrder);

    communicationMessageRepository.save(message);
  }

  private void saveChildMessageIfSessionExists(
      Long userId, Long sessionId, String recognizedText, String audioMimeType, String locale) {
    if (sessionId == null) {
      return;
    }

    CommunicationSession session = getActiveSessionForUpdate(userId, sessionId);
    int messageOrder = session.allocateNextMessageOrder();

    CommunicationMessage message =
        CommunicationMessage.childSpeechToParentText(
            session, recognizedText, recognizedText, audioMimeType, locale, messageOrder);

    communicationMessageRepository.save(message);
  }

  private CommunicationSession getActiveSessionForUpdate(Long userId, Long sessionId) {
    if (userId == null) {
      throw new BusinessException(CommunicationSessionErrorCode.SESSION_NOT_FOUND);
    }

    return communicationSessionRepository
        .findByIdAndUserIdAndStatusForUpdate(sessionId, userId, CommunicationSessionStatus.ACTIVE)
        .orElseThrow(() -> new BusinessException(CommunicationSessionErrorCode.SESSION_NOT_FOUND));
  }

  private String resolveTtsSpeaker(Long userId) {
    if (userId == null) {
      return TtsSpeaker.MOM_WARM.getCode();
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

    return user.getTtsSpeaker();
  }

  private void validateAudioFile(MultipartFile audioFile) {
    if (audioFile == null || audioFile.isEmpty()) {
      log.warn("[STT] Invalid empty audio file. traceId={}", getTraceId());
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }
  }

  private String resolveAudioMimeType(MultipartFile audioFile, String audioMimeType) {
    String requestMimeType = normalizeMimeType(audioMimeType);
    String multipartMimeType = normalizeMimeType(audioFile.getContentType());

    if (requestMimeType != null && !SUPPORTED_AUDIO_MIME_TYPES.contains(requestMimeType)) {
      log.warn(
          "[STT] Unsupported request audio mime type. traceId={}, originalFilename={}, requestAudioMimeType={}",
          getTraceId(),
          audioFile.getOriginalFilename(),
          requestMimeType);
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }

    if (multipartMimeType != null && !SUPPORTED_AUDIO_MIME_TYPES.contains(multipartMimeType)) {
      log.warn(
          "[STT] Unsupported multipart audio mime type. traceId={}, originalFilename={}, multipartContentType={}",
          getTraceId(),
          audioFile.getOriginalFilename(),
          multipartMimeType);
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }

    String resolvedAudioMimeType = requestMimeType != null ? requestMimeType : multipartMimeType;

    if (resolvedAudioMimeType == null) {
      log.warn(
          "[STT] Missing audio mime type. traceId={}, originalFilename={}",
          getTraceId(),
          audioFile.getOriginalFilename());
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }

    return resolvedAudioMimeType;
  }

  private void validateWavHeader(MultipartFile audioFile) {
    try {
      byte[] header = audioFile.getInputStream().readNBytes(12);

      boolean isWav =
          header.length >= 12
              && header[0] == 'R'
              && header[1] == 'I'
              && header[2] == 'F'
              && header[3] == 'F'
              && header[8] == 'W'
              && header[9] == 'A'
              && header[10] == 'V'
              && header[11] == 'E';

      if (!isWav) {
        log.warn(
            "[STT] Invalid WAV header. traceId={}, originalFilename={}, multipartContentType={}",
            getTraceId(),
            audioFile.getOriginalFilename(),
            audioFile.getContentType());
        throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
      }

    } catch (IOException e) {
      log.warn(
          "[STT] Failed to read WAV header. traceId={}, originalFilename={}, exceptionClass={}, message={}",
          getTraceId(),
          audioFile.getOriginalFilename(),
          e.getClass().getName(),
          e.getMessage());
      throw new BusinessException(TranslationErrorCode.INVALID_AUDIO);
    }
  }

  private String resolveLocale(String locale) {
    String resolvedLocale = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;

    if (!DEFAULT_LOCALE.equals(resolvedLocale)) {
      log.warn("[STT] Unsupported locale. traceId={}, locale={}", getTraceId(), locale);
      throw new BusinessException(TranslationErrorCode.INVALID_LOCALE);
    }

    return resolvedLocale;
  }

  private String normalizeMimeType(String mimeType) {
    return mimeType == null || mimeType.isBlank() ? null : mimeType.trim().toLowerCase(Locale.ROOT);
  }

  private void logSpeechToTextRequest(
      MultipartFile audioFile, String locale, String audioMimeType) {
    if (audioFile == null) {
      log.info(
          "[STT] Request received. traceId={}, audioFile=null, requestAudioMimeType={}, locale={}",
          getTraceId(),
          audioMimeType,
          locale);
      return;
    }

    log.info(
        "[STT] Request received. traceId={}, originalFilename={}, multipartContentType={}, size={}, requestAudioMimeType={}, locale={}",
        getTraceId(),
        audioFile.getOriginalFilename(),
        audioFile.getContentType(),
        audioFile.getSize(),
        audioMimeType,
        locale);
  }

  private int getTextLength(String text) {
    return text == null ? 0 : text.length();
  }

  private String getTraceId() {
    String traceId = MDC.get("traceId");
    return traceId != null ? traceId : "-";
  }
}
