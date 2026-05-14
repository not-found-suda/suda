package com.ssafy.backend.domain.translation.controller;

import com.ssafy.backend.domain.translation.docs.TranslationApiDocs;
import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import com.ssafy.backend.domain.translation.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/translation")
public class TranslationController implements TranslationApiDocs {

  private final TranslationService translationService;

  public TranslationController(TranslationService translationService) {
    this.translationService = translationService;
  }

  @PostMapping("/sign-to-speech")
  @Override
  public ResponseEntity<SignToSpeechResponseDto> translateSignToSpeech(
      Authentication authentication, @Valid @RequestBody SignToSpeechRequestDto requestDto) {

    Long userId = extractNullableUserId(authentication);

    return ResponseEntity.ok(translationService.translateSignToSpeech(userId, requestDto));
  }

  @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Override
  public ResponseEntity<SpeechToTextResponseDto> translateSpeechToText(
      @RequestPart("audioFile") MultipartFile audioFile,
      @RequestPart(value = "locale", required = false) String locale,
      @RequestPart(value = "audioMimeType", required = false) String audioMimeType) {

    return ResponseEntity.ok(
        translationService.translateSpeechToText(audioFile, locale, audioMimeType));
  }

  private Long extractNullableUserId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }

    Object principal = authentication.getPrincipal();

    if (principal instanceof Long userId) {
      return userId;
    }

    return null;
  }
}
