package com.ssafy.backend.global.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.backend.domain.auth.service.AccessTokenBlacklistStore;
import com.ssafy.backend.global.security.ProblemDetailAccessDeniedHandler;
import com.ssafy.backend.global.security.ProblemDetailAuthenticationEntryPoint;
import com.ssafy.backend.global.security.jwt.JwtAuthenticationFilter;
import com.ssafy.backend.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(SecurityAccessPolicyTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(
    properties = {
      "auth.jwt.secret=test-jwt-secret-at-least-32-bytes",
      "auth.jwt.issuer=test",
      "auth.jwt.access-token-ttl-seconds=900",
      "auth.jwt.refresh-token-ttl-seconds=1209600"
    })
class SecurityAccessPolicyTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("번역 API는 로그인 없이 호출할 수 있다")
  void translationApisArePublic() throws Exception {
    mockMvc.perform(post("/api/v1/translation/sign-to-speech")).andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/translation/speech-to-text")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("인증 공개 API는 로그인 없이 호출할 수 있다")
  void authPublicApisArePublic() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup")).andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/auth/oauth/naver")).andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("로그아웃과 인증 상태 조회는 로그인이 필요하다")
  void authProtectedApisRequireAuthentication() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/auth/status")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("사용자, 아이 프로필, 학습, 퀴즈 API는 로그인이 필요하다")
  void protectedDomainApisRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(patch("/api/v1/users/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/children")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/children")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/learn/categories")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/quiz/sessions")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CORS preflight 요청은 로그인 없이 허용한다")
  void optionsRequestsArePublic() throws Exception {
    mockMvc.perform(options("/api/v1/users/me")).andExpect(status().isOk());
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import({
    SecurityConfig.class,
    ProblemDetailAuthenticationEntryPoint.class,
    ProblemDetailAccessDeniedHandler.class,
    JwtAuthenticationFilter.class,
    TestAccessController.class
  })
  static class TestConfig {

    @Bean
    JwtTokenProvider jwtTokenProvider() {
      return Mockito.mock(JwtTokenProvider.class);
    }

    @Bean
    AccessTokenBlacklistStore accessTokenBlacklistStore() {
      return Mockito.mock(AccessTokenBlacklistStore.class);
    }
  }

  @RestController
  static class TestAccessController {

    @PostMapping("/api/v1/translation/sign-to-speech")
    ResponseEntity<Void> signToSpeech() {
      return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/translation/speech-to-text")
    ResponseEntity<Void> speechToText() {
      return ResponseEntity.ok().build();
    }

    @PostMapping({
      "/api/v1/auth/signup",
      "/api/v1/auth/login",
      "/api/v1/auth/oauth/naver",
      "/api/v1/auth/refresh",
      "/api/v1/auth/logout",
      "/api/v1/children",
      "/api/v1/quiz/sessions"
    })
    ResponseEntity<Void> postEndpoint() {
      return ResponseEntity.ok().build();
    }

    @GetMapping({"/api/v1/auth/status", "/api/v1/users/me", "/api/v1/children"})
    ResponseEntity<Void> getEndpoint() {
      return ResponseEntity.ok().build();
    }

    @PatchMapping("/api/v1/users/me")
    ResponseEntity<Void> patchEndpoint() {
      return ResponseEntity.ok().build();
    }

    @RequestMapping("/api/v1/learn/categories")
    ResponseEntity<Void> learnEndpoint() {
      return ResponseEntity.ok().build();
    }
  }
}
