package com.ssafy.backend.domain.translation.service;

import com.ssafy.backend.domain.translation.dto.SignToSpeechRequestDto;
import com.ssafy.backend.domain.translation.dto.SignToSpeechResponseDto;
import com.ssafy.backend.domain.translation.dto.SpeechToTextResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface TranslationService {

  SignToSpeechResponseDto translateSignToSpeech(Long userId, SignToSpeechRequestDto requestDto);

  SpeechToTextResponseDto translateSpeechToText(
      Long userId, Long sessionId, MultipartFile audioFile, String locale, String audioMimeType);
}
