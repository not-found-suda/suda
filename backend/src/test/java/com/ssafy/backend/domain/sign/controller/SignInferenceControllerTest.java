package com.ssafy.backend.domain.sign.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.domain.sign.service.SignInferenceService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SignInferenceControllerTest {

  @Mock private SignInferenceService signInferenceService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SignInferenceController(signInferenceService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("수어 인식 API가 정상 응답을 반환한다")
  void predictReturnsSignInferenceResponse() throws Exception {
    when(signInferenceService.predict(any(SignInferenceRequestDto.class))).thenReturn(response());

    mockMvc
        .perform(
            post("/api/v1/sign/inference")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gloss").value("병원"))
        .andExpect(jsonPath("$.accepted").value(true))
        .andExpect(jsonPath("$.modelVersion").value("v6_24words_tcn"))
        .andExpect(jsonPath("$.traceId").value("trace-1"));
  }

  @Test
  @DisplayName("features가 비어 있으면 요청 검증 오류를 반환한다")
  void predictRejectsEmptyFeatures() throws Exception {
    SignInferenceRequestDto request =
        new SignInferenceRequestDto(null, 30, 332, List.of(), null, 3);

    mockMvc
        .perform(
            post("/api/v1/sign/inference")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_REQUIRED_FIELD"));

    verifyNoInteractions(signInferenceService);
  }

  @Test
  @DisplayName("AI 서버 timeout은 504 ProblemDetail로 반환한다")
  void predictMapsAiServerTimeout() throws Exception {
    when(signInferenceService.predict(any(SignInferenceRequestDto.class)))
        .thenThrow(new BusinessException(SignInferenceErrorCode.AI_SERVER_TIMEOUT));

    mockMvc
        .perform(
            post("/api/v1/sign/inference")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("SIGN_INFERENCE_AI_SERVER_TIMEOUT"));
  }

  private SignInferenceRequestDto validRequest() {
    return new SignInferenceRequestDto(null, 30, 332, Collections.nCopies(30 * 332, 0.0f), null, 3);
  }

  private SignInferenceResponseDto response() {
    return new SignInferenceResponseDto(
        "병원", 0.92f, 0.2f, 19, "병원", true, null, List.of(), "v6_24words_tcn", 18L, "trace-1");
  }
}
