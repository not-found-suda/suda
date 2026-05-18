package com.ssafy.backend.domain.translation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SignToSpeechResponseDto(
    @Schema(description = "서버가 입력으로 받은 원본 단어 배열", example = "[\"엄마\", \"해보다\"]")
        List<String> originalWords,
    @Schema(description = "Gemini 문맥 보정 후 생성된 자연스러운 문장", example = "엄마 해봐!") String correctedText,
    @Schema(description = "Clova TTS로 생성된 음성 데이터(Base64)", example = "BASE64_ENCODED_AUDIO")
        String audioBase64,
    @Schema(description = "오디오 MIME 타입", example = "audio/mpeg") String audioMimeType,
    @Schema(description = "원본 단어열 기준으로 문맥 보정이 수행되었는지 여부", example = "true") boolean corrected) {}
