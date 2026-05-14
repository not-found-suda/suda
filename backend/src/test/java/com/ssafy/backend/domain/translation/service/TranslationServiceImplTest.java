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
import java.util.Optional;
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
  @Mock private UserRepository userRepository;

  private TranslationServiceImpl translationService;

  @BeforeEach
  void setUp() {
    translationService =
        new TranslationServiceImpl(
            signLanguageCorrectionClient, clovaTtsClient, clovaSttClient, userRepository);
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

  @Test
  @DisplayName("audio/wave MIME type도 STT 요청에 사용할 수 있다")
  void translateSpeechToTextAllowsAudioWaveMimeType() {
    MockMultipartFile audio = wavAudio("audio/wave");
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wave")))
        .thenReturn("안녕하세요");

    SpeechToTextResponseDto response =
        translationService.translateSpeechToText(audio, "ko-KR", null);

    assertThat(response.recognizedText()).isEqualTo("안녕하세요");
    assertThat(response.locale()).isEqualTo("ko-KR");
    verify(clovaSttClient).transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/wave"));
  }

  @Test
  @DisplayName("audio/x-wav MIME type도 STT 요청에 사용할 수 있다")
  void translateSpeechToTextAllowsAudioXWavMimeType() {
    MockMultipartFile audio = wavAudio("audio/x-wav");
    when(clovaSttClient.transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/x-wav")))
        .thenReturn("안녕하세요");

    SpeechToTextResponseDto response =
        translationService.translateSpeechToText(audio, "ko-KR", null);

    assertThat(response.recognizedText()).isEqualTo("안녕하세요");
    assertThat(response.locale()).isEqualTo("ko-KR");
    verify(clovaSttClient).transcribe(any(MultipartFile.class), eq("ko-KR"), eq("audio/x-wav"));
  }

  @Test
  @DisplayName("수어 단어를 보정하고 TTS 음성을 Base64로 반환한다")
  void translateSignToSpeechReturnsCorrectedTextAndAudio() {
    SignToSpeechRequestDto request = new SignToSpeechRequestDto(List.of("엄마", "해보다"), null, true);
    byte[] audioBytes = new byte[] {1, 2, 3};
    when(signLanguageCorrectionClient.correct("[엄마, 해보다]")).thenReturn("엄마 해봐!");
    when(clovaTtsClient.synthesize("엄마 해봐!", TtsSpeaker.MOM_WARM.getCode())).thenReturn(audioBytes);
    when(clovaTtsClient.getAudioMimeType()).thenReturn("audio/mpeg");

    SignToSpeechResponseDto response = translationService.translateSignToSpeech(null, request);

    assertThat(response.originalWords()).containsExactly("엄마", "해보다");
    assertThat(response.correctedText()).isEqualTo("엄마 해봐!");
    assertThat(response.audioBase64()).isEqualTo(Base64.getEncoder().encodeToString(audioBytes));
    assertThat(response.audioMimeType()).isEqualTo("audio/mpeg");
    assertThat(response.corrected()).isTrue();
    verify(signLanguageCorrectionClient).correct("[엄마, 해보다]");
    verify(clovaTtsClient).synthesize("엄마 해봐!", TtsSpeaker.MOM_WARM.getCode());
    verify(clovaTtsClient).getAudioMimeType();
    verifyNoInteractions(userRepository);
    verifyNoInteractions(clovaSttClient);
  }

  @Test
  @DisplayName("TTS 요청값이 없으면 기본으로 음성을 생성한다")
  void translateSignToSpeechRequestsTtsByDefault() {
    SignToSpeechRequestDto request = new SignToSpeechRequestDto(List.of("안녕"), "ko-KR", null);
    byte[] audioBytes = new byte[] {4, 5, 6};
    when(signLanguageCorrectionClient.correct("[안녕]")).thenReturn("안녕");
    when(clovaTtsClient.synthesize("안녕", TtsSpeaker.MOM_WARM.getCode())).thenReturn(audioBytes);
    when(clovaTtsClient.getAudioMimeType()).thenReturn("audio/mpeg");

    SignToSpeechResponseDto response = translationService.translateSignToSpeech(null, request);

    assertThat(response.correctedText()).isEqualTo("안녕");
    assertThat(response.audioBase64()).isEqualTo(Base64.getEncoder().encodeToString(audioBytes));
    assertThat(response.audioMimeType()).isEqualTo("audio/mpeg");
    assertThat(response.corrected()).isFalse();
    verify(clovaTtsClient).synthesize("안녕", TtsSpeaker.MOM_WARM.getCode());
  }

  @Test
  @DisplayName("로그인 사용자의 TTS speaker 설정으로 음성을 생성한다")
  void translateSignToSpeechUsesUserTtsSpeaker() {
    Long userId = 1L;
    User user = User.create("guardian@example.com", "encoded-password", "보호자");
    user.updateTtsSpeaker(TtsSpeaker.DAD_CALM.getCode());
    SignToSpeechRequestDto request = new SignToSpeechRequestDto(List.of("아빠"), "ko-KR", true);
    byte[] audioBytes = new byte[] {7, 8, 9};
    when(signLanguageCorrectionClient.correct("[아빠]")).thenReturn("아빠");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(clovaTtsClient.synthesize("아빠", TtsSpeaker.DAD_CALM.getCode())).thenReturn(audioBytes);
    when(clovaTtsClient.getAudioMimeType()).thenReturn("audio/mpeg");

    SignToSpeechResponseDto response = translationService.translateSignToSpeech(userId, request);

    assertThat(response.audioBase64()).isEqualTo(Base64.getEncoder().encodeToString(audioBytes));
    verify(userRepository).findById(userId);
    verify(clovaTtsClient).synthesize("아빠", TtsSpeaker.DAD_CALM.getCode());
  }

  @Test
  @DisplayName("로그인 사용자를 찾을 수 없으면 TTS 요청을 실패 처리한다")
  void translateSignToSpeechRejectsMissingUser() {
    Long userId = 999L;
    SignToSpeechRequestDto request = new SignToSpeechRequestDto(List.of("엄마"), "ko-KR", true);
    when(signLanguageCorrectionClient.correct("[엄마]")).thenReturn("엄마");
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> translationService.translateSignToSpeech(userId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    verify(userRepository).findById(userId);
    verifyNoInteractions(clovaTtsClient, clovaSttClient);
  }

  @Test
  @DisplayName("TTS 요청값이 false이면 문장 보정만 수행한다")
  void translateSignToSpeechSkipsTtsWhenRequestTtsIsFalse() {
    SignToSpeechRequestDto request =
        new SignToSpeechRequestDto(List.of("엄마", "해보다"), "ko-KR", false);
    when(signLanguageCorrectionClient.correct("[엄마, 해보다]")).thenReturn("엄마 해봐!");

    SignToSpeechResponseDto response = translationService.translateSignToSpeech(1L, request);

    assertThat(response.correctedText()).isEqualTo("엄마 해봐!");
    assertThat(response.audioBase64()).isNull();
    assertThat(response.audioMimeType()).isNull();
    assertThat(response.corrected()).isTrue();
    verify(signLanguageCorrectionClient).correct("[엄마, 해보다]");
    verifyNoInteractions(userRepository);
    verifyNoInteractions(clovaTtsClient, clovaSttClient);
  }

  @Test
  @DisplayName("수어 문맥 보정 실패는 보정 실패 오류로 처리한다")
  void translateSignToSpeechMapsCorrectionFailure() {
    SignToSpeechRequestDto request = new SignToSpeechRequestDto(List.of("엄마"), "ko-KR", true);
    when(signLanguageCorrectionClient.correct("[엄마]"))
        .thenThrow(new IllegalStateException("correction failed"));

    assertTranslationError(
        () -> translationService.translateSignToSpeech(1L, request),
        TranslationErrorCode.SIGN_CORRECTION_FAILED);
    verifyNoInteractions(userRepository);
    verifyNoInteractions(clovaTtsClient, clovaSttClient);
  }

  @Test
  @DisplayName("TTS 실패는 음성 합성 실패 오류로 처리한다")
  void translateSignToSpeechMapsTtsFailure() {
    SignToSpeechRequestDto request = new SignToSpeechRequestDto(List.of("엄마"), "ko-KR", true);
    when(signLanguageCorrectionClient.correct("[엄마]")).thenReturn("엄마");
    when(clovaTtsClient.synthesize("엄마", TtsSpeaker.MOM_WARM.getCode()))
        .thenThrow(new IllegalStateException("tts failed"));

    assertTranslationError(
        () -> translationService.translateSignToSpeech(null, request),
        TranslationErrorCode.TEXT_TO_SPEECH_FAILED);
    verify(clovaTtsClient).synthesize("엄마", TtsSpeaker.MOM_WARM.getCode());
    verifyNoInteractions(userRepository);
    verifyNoInteractions(clovaSttClient);
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
