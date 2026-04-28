package com.ssafy.backend;

import com.ssafy.backend.domain.comms.service.SignLanguageCorrectionClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TestRunner implements CommandLineRunner {

  private final SignLanguageCorrectionClient signLanguageCorrectionClient;

  public TestRunner(SignLanguageCorrectionClient signLanguageCorrectionClient) {
    this.signLanguageCorrectionClient = signLanguageCorrectionClient;
  }

  @Override
  public void run(String... args) {
    System.out.println("===== Gemini 수어 문맥 보정 테스트 시작 =====");

    String signText = "[물, 마시다, 원하다]";

    String correctedText = signLanguageCorrectionClient.correct(signText);

    System.out.println("수어 모델 결과: " + signText);
    System.out.println("보정 문장: " + correctedText);

    System.out.println("===== Gemini 수어 문맥 보정 테스트 종료 =====");
  }
}
