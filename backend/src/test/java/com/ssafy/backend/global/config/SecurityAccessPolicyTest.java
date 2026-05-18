package com.ssafy.backend.global.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.backend.domain.auth.service.AccessTokenBlacklistStore;
import com.ssafy.backend.domain.auth.service.AccessTokenInvalidationStore;
import com.ssafy.backend.global.security.ProblemDetailAccessDeniedHandler;
import com.ssafy.backend.global.security.ProblemDetailAuthenticationEntryPoint;
import com.ssafy.backend.global.security.Role;
import com.ssafy.backend.global.security.jwt.JwtAuthenticationFilter;
import com.ssafy.backend.global.security.jwt.JwtTokenProvider;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

  private static final String VALID_ACCESS_TOKEN = "valid-access-token";
  private static final Instant VALID_ACCESS_TOKEN_ISSUED_AT = Instant.parse("2026-05-17T00:00:00Z");

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private AccessTokenBlacklistStore accessTokenBlacklistStore;
  @Autowired private AccessTokenInvalidationStore accessTokenInvalidationStore;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Mockito.reset(jwtTokenProvider, accessTokenBlacklistStore, accessTokenInvalidationStore);
    Mockito.when(jwtTokenProvider.validateToken(VALID_ACCESS_TOKEN)).thenReturn(true);
    Mockito.when(jwtTokenProvider.isAccessToken(VALID_ACCESS_TOKEN)).thenReturn(true);
    Mockito.when(jwtTokenProvider.getJti(VALID_ACCESS_TOKEN)).thenReturn("valid-access-jti");
    Mockito.when(jwtTokenProvider.getUserId(VALID_ACCESS_TOKEN)).thenReturn(1L);
    Mockito.when(jwtTokenProvider.getIssuedAt(VALID_ACCESS_TOKEN))
        .thenReturn(VALID_ACCESS_TOKEN_ISSUED_AT);
    Mockito.when(jwtTokenProvider.getRole(VALID_ACCESS_TOKEN)).thenReturn(Role.USER);
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
  @DisplayName("헬스 체크와 API 문서 경로는 로그인 없이 호출할 수 있다")
  void docsAndHealthApisArePublic() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isOk());
    mockMvc.perform(get("/error")).andExpect(status().isOk());
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
    mockMvc.perform(delete("/api/v1/users/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/children")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/children")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/learn/categories")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/learn/quizzes/sessions")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("유효한 액세스 토큰이 있으면 보호 API를 호출할 수 있다")
  void protectedApiAllowsValidAccessToken() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + VALID_ACCESS_TOKEN))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("무효화 기준 시각 이전에 발급된 액세스 토큰은 보호 API를 호출할 수 없다")
  void protectedApiRejectsInvalidatedAccessToken() throws Exception {
    Mockito.when(accessTokenInvalidationStore.isInvalidated(1L, VALID_ACCESS_TOKEN_ISSUED_AT))
        .thenReturn(true);

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + VALID_ACCESS_TOKEN))
        .andExpect(status().isUnauthorized());
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

    @Bean
    AccessTokenInvalidationStore accessTokenInvalidationStore() {
      return Mockito.mock(AccessTokenInvalidationStore.class);
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
      "/api/v1/learn/quizzes/sessions"
    })
    ResponseEntity<Void> postEndpoint() {
      return ResponseEntity.ok().build();
    }

    @GetMapping({
      "/api/v1/auth/status",
      "/api/v1/users/me",
      "/api/v1/children",
      "/api/v1/health",
      "/v3/api-docs",
      "/swagger-ui/index.html",
      "/swagger-ui.html",
      "/error"
    })
    ResponseEntity<Void> getEndpoint() {
      return ResponseEntity.ok().build();
    }

    @PatchMapping("/api/v1/users/me")
    ResponseEntity<Void> patchEndpoint() {
      return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/v1/users/me")
    ResponseEntity<Void> deleteEndpoint() {
      return ResponseEntity.ok().build();
    }

    @RequestMapping("/api/v1/learn/categories")
    ResponseEntity<Void> learnEndpoint() {
      return ResponseEntity.ok().build();
    }
  }
}
