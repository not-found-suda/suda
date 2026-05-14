package com.ssafy.backend.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.comms.service.ClovaSttClient;
import com.ssafy.backend.domain.comms.service.ClovaTtsClient;
import com.ssafy.backend.domain.comms.service.SignLanguageCorrectionClient;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import com.ssafy.backend.domain.translation.exception.TranslationErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class TranslationServiceImplTest {

  @Mock private SignLanguageCorrectionClient signLanguageCorrectionClient;
  @Mock private ClovaTtsClient clovaTtsClient;
  @Mock private ClovaSttClient clovaSttClient;

  private TranslationServiceImpl translationService;

  @BeforeEach
  void setUp() {
    translationService =
        new TranslationServiceImpl(signLanguageCorrectionClient, clovaTtsClient, clovaSttClient);
  }

  @Test
  @DisplayName("음성 파일이 없으면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsMissingAudioFile() {
    assertTranslationError(
        () -> translationService.translateSpeechToText(null, "ko-KR", "audio/wav"),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("빈 음성 파일이면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsEmptyAudioFile() {
    MockMultipartFile emptyAudio =
        new MockMultipartFile("audioFile", "answer.wav", "audio/wav", new byte[0]);

    assertTranslationError(
        () -> translationService.translateSpeechToText(emptyAudio, "ko-KR", "audio/wav"),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("요청 MIME type이 지원되지 않으면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsUnsupportedRequestMimeType() {
    MockMultipartFile audio = wavAudio("audio/wav");

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, "ko-KR", "audio/mpeg"),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("Multipart MIME type이 지원되지 않으면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsUnsupportedMultipartMimeType() {
    MockMultipartFile audio = wavAudio("audio/mpeg");

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, "ko-KR", null),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("MIME type을 확인할 수 없으면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsMissingMimeType() {
    MockMultipartFile audio = wavAudio(null);

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, "ko-KR", null),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("WAV 헤더가 아니면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsInvalidWavHeader() {
    MockMultipartFile audio =
        new MockMultipartFile("audioFile", "answer.wav", "audio/wav", "not-wav-data".getBytes());

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, "ko-KR", "audio/wav"),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("음성 파일을 읽을 수 없으면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsUnreadableAudioFile() throws IOException {
    MultipartFile audioFile = org.mockito.Mockito.mock(MultipartFile.class);
    when(audioFile.isEmpty()).thenReturn(false);
    when(audioFile.getContentType()).thenReturn("audio/wav");
    when(audioFile.getOriginalFilename()).thenReturn("answer.wav");
    when(audioFile.getSize()).thenReturn(12L);
    when(audioFile.getInputStream()).thenThrow(new IOException("read failed"));

    assertTranslationError(
        () -> translationService.translateSpeechToText(audioFile, "ko-KR", "audio/wav"),
        TranslationErrorCode.INVALID_AUDIO);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("지원하지 않는 locale이면 STT 요청을 거부한다")
  void translateSpeechToTextRejectsUnsupportedLocale() {
    MockMultipartFile audio = wavAudio("audio/wav");

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, "en-US", "audio/wav"),
        TranslationErrorCode.INVALID_LOCALE);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("STT 결과가 비어 있으면 인식 불가로 처리한다")
  void translateSpeechToTextRejectsBlankRecognizedText() {
    MockMultipartFile audio = wavAudio("audio/wav");
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav")))
        .thenReturn("   ");

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, null, " audio/WAV "),
        TranslationErrorCode.UNRECOGNIZABLE_AUDIO);
  }

  @Test
  @DisplayName("STT 클라이언트 예외는 음성 인식 실패로 처리한다")
  void translateSpeechToTextMapsSttClientException() {
    MockMultipartFile audio = wavAudio("audio/wav");
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav")))
        .thenThrow(new IllegalStateException("stt failed"));

    assertTranslationError(
        () -> translationService.translateSpeechToText(audio, "ko-KR", "audio/wav"),
        TranslationErrorCode.SPEECH_RECOGNITION_FAILED);
  }

  @Test
  @DisplayName("STT 성공 시 인식 텍스트와 처리 locale을 반환한다")
  void translateSpeechToTextReturnsRecognizedText() {
    MockMultipartFile audio = wavAudio("audio/wav");
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav")))
        .thenReturn("안녕하세요");

    SpeechToTextResponseDto response =
        translationService.translateSpeechToText(audio, null, "audio/wav");

    assertThat(response.recognizedText()).isEqualTo("안녕하세요");
    assertThat(response.correctedText()).isEqualTo("안녕하세요");
    assertThat(response.corrected()).isFalse();
    assertThat(response.confidence()).isNull();
    assertThat(response.locale()).isEqualTo("ko-KR");
    verify(clovaSttClient).transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wav"));
    verifyNoInteractions(signLanguageCorrectionClient, clovaTtsClient);
  }

  private MockMultipartFile wavAudio(String contentType) {
    return new MockMultipartFile("audioFile", "answer.wav", contentType, wavBytes());
  }

  private byte[] wavBytes() {
    return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E', 1, 2, 3, 4};
  }

  private void assertTranslationError(ThrowingCall call, TranslationErrorCode expectedErrorCode) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
