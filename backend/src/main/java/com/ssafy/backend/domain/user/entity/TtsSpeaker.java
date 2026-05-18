package com.ssafy.backend.domain.user.entity;

import java.util.Arrays;

public enum TtsSpeaker {
  MOM_WARM("vara", "따뜻한 엄마 목소리"),
  MOM_BRIGHT("vgoeun", "밝은 엄마 목소리"),
  DAD_WARM("vdonghyun", "다정한 아빠 목소리"),
  DAD_CALM("neunwoo", "차분한 아빠 목소리");

  private final String code;
  private final String label;

  TtsSpeaker(String code, String label) {
    this.code = code;
    this.label = label;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public static boolean isSupported(String code) {
    return Arrays.stream(values()).anyMatch(speaker -> speaker.code.equals(code));
  }

  public static TtsSpeaker fromCode(String code) {
    return Arrays.stream(values())
        .filter(speaker -> speaker.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 TTS speaker입니다."));
  }
}
