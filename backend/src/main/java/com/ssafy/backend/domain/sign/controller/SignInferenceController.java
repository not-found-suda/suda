package com.ssafy.backend.domain.sign.controller;

import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.service.SignInferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sign")
public class SignInferenceController {

  private final SignInferenceService signInferenceService;

  public SignInferenceController(SignInferenceService signInferenceService) {
    this.signInferenceService = signInferenceService;
  }

  @PostMapping("/inference")
  public ResponseEntity<SignInferenceResponseDto> predict(
      @Valid @RequestBody SignInferenceRequestDto requestDto) {
    return ResponseEntity.ok(signInferenceService.predict(requestDto));
  }
}
