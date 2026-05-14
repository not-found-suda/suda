package com.ssafy.backend.domain.translation.docs;

import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Translation API", description = "수어/음성 번역 API")
public interface TranslationApiDocs {

  @Operation(
      summary = "수어 텍스트 및 음성 변환",
      description =
          "수어 단어 배열을 받아 문맥 보정 후 TTS 음성을 생성하여 반환합니다. 비로그인 사용자는 기본 목소리(vara)를 사용하고, 로그인 사용자는 저장된 TTS 목소리를 사용합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "VALIDATION_INVALID_INPUT",
    "USER_NOT_FOUND",
    "TRANSLATION_SIGN_CORRECTION_FAILED",
    "TRANSLATION_TEXT_TO_SPEECH_FAILED"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = SignToSpeechResponseDto.class)))
  ResponseEntity<SignToSpeechResponseDto> translateSignToSpeech(
      @Parameter(hidden = true) Authentication authentication,
      @Valid @RequestBody SignToSpeechRequestDto requestDto);

  @Operation(summary = "음성 텍스트 변환", description = "자녀 음성 파일을 받아 Clova STT로 인식한 텍스트를 그대로 반환합니다.")
  @ApiErrorCodes({
    "TRANSLATION_INVALID_AUDIO",
    "TRANSLATION_INVALID_LOCALE",
    "TRANSLATION_UNRECOGNIZABLE_AUDIO",
    "TRANSLATION_SPEECH_RECOGNITION_FAILED"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = SpeechToTextResponseDto.class)))
  ResponseEntity<SpeechToTextResponseDto> translateSpeechToText(
      @RequestPart("audioFile") MultipartFile audioFile,
      @RequestPart(value = "locale", required = false) String locale,
      @RequestPart(value = "audioMimeType", required = false) String audioMimeType);
}
