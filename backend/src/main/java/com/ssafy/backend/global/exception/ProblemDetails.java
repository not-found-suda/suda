package com.ssafy.backend.global.exception;

import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

  private ProblemDetails() {}

  public static ProblemDetail of(ErrorCode errorCode, String instance) {
    return of(errorCode, instance, errorCode.getMessage());
  }

  public static ProblemDetail of(ErrorCode errorCode, String instance, String detailMessage) {
    String resolvedDetail =
        detailMessage == null || detailMessage.isBlank() ? errorCode.getMessage() : detailMessage;
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.getHttpStatus(), resolvedDetail);
    problemDetail.setType(URI.create("urn:s14p31a404:error:" + toKebabCase(errorCode.getCode())));
    problemDetail.setTitle(ProblemDetailConventions.resolveTitle(errorCode));
    problemDetail.setInstance(URI.create(ProblemDetailConventions.normalizeInstance(instance)));
    problemDetail.setProperty("code", errorCode.getCode());

    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      problemDetail.setProperty("traceId", traceId);
    }
    return problemDetail;
  }

  private static String toKebabCase(String value) {
    return value.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
