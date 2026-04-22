package com.ssafy.backend.global.controller;

import com.ssafy.backend.global.docs.HealthApiDocs;
import com.ssafy.backend.global.dto.HealthResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController implements HealthApiDocs {

  @GetMapping("/health")
  @Override
  public ResponseEntity<HealthResponseDto> health() {
    return ResponseEntity.ok(new HealthResponseDto("UP"));
  }
}
