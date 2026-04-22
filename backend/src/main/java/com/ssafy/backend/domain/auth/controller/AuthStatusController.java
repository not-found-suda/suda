package com.ssafy.backend.domain.auth.controller;

import com.ssafy.backend.domain.auth.docs.AuthStatusApiDocs;
import com.ssafy.backend.domain.auth.dto.AuthStatusResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthStatusController implements AuthStatusApiDocs {

  @GetMapping("/status")
  @Override
  public ResponseEntity<AuthStatusResponseDto> getAuthStatus() {
    return ResponseEntity.ok(new AuthStatusResponseDto("authenticated"));
  }
}
