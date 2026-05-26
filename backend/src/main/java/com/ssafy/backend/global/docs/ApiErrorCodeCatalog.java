package com.ssafy.backend.global.docs;

import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.report.exception.ReportErrorCode;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.domain.translation.exception.TranslationErrorCode;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.global.exception.CommonErrorCode;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorCodeCatalog {

  private final Map<String, ErrorCode> byCode;

  public ApiErrorCodeCatalog() {
    Map<String, ErrorCode> map = new LinkedHashMap<>();
    register(map, CommonErrorCode.values());
    register(map, ValidationErrorCode.values());
    register(map, AuthErrorCode.values());
    register(map, ChildProfileErrorCode.values());
    register(map, ReportErrorCode.values());
    register(map, SignInferenceErrorCode.values());
    register(map, TranslationErrorCode.values());
    register(map, UserErrorCode.values());
    this.byCode = Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }

  public Optional<ErrorCode> findByCode(String code) {
    if (code == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(byCode.get(code));
  }

  private static void register(Map<String, ErrorCode> target, ErrorCode[] errorCodes) {
    for (ErrorCode errorCode : errorCodes) {
      ErrorCode existing = target.putIfAbsent(errorCode.getCode(), errorCode);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate API error code detected: " + errorCode.getCode());
      }
    }
  }
}
