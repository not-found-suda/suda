package com.ssafy.backend.global.exception;

import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.auth.exception.OAuthErrorCode;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.social.exception.SocialAccountErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final int MAX_VALIDATION_ERRORS = 5;

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusinessException(
      BusinessException exception, HttpServletRequest request) {
    ErrorCode errorCode = exception.getErrorCode();
    log.warn(
        "Business exception: method={}, uri={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        errorCode.getCode(),
        exception.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ProblemDetails.of(errorCode, request.getRequestURI(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    return handleValidationException(exception.getBindingResult(), request);
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ProblemDetail> handleBindException(
      BindException exception, HttpServletRequest request) {
    return handleValidationException(exception.getBindingResult(), request);
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ProblemDetail> handleMissingServletRequestPartException(
      MissingServletRequestPartException exception, HttpServletRequest request) {
    ValidationErrorCode errorCode = ValidationErrorCode.REQUIRED_FIELD;
    String message = "필수 요청 파트가 누락되었습니다.";
    log.warn(
        "Missing request part: method={}, uri={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        errorCode.getCode(),
        exception.getMessage());

    ProblemDetail problemDetail = ProblemDetails.of(errorCode, request.getRequestURI(), message);
    problemDetail.setProperty(
        "errors",
        List.of(
            Map.of(
                "field", exception.getRequestPartName(),
                "code", errorCode.getCode(),
                "message", message)));
    problemDetail.setProperty("errorCount", 1);

    return ResponseEntity.status(errorCode.getHttpStatus()).body(problemDetail);
  }

  private ResponseEntity<ProblemDetail> handleValidationException(
      BindingResult bindingResult, HttpServletRequest request) {
    List<FieldError> fieldErrors = bindingResult.getFieldErrors();

    ValidationErrorCode errorCode =
        fieldErrors.isEmpty()
            ? ValidationErrorCode.INVALID_INPUT
            : ValidationErrorCode.from(fieldErrors.get(0));
    String message =
        fieldErrors.isEmpty()
            ? errorCode.getMessage()
            : resolveValidationMessage(fieldErrors.get(0), errorCode);
    log.warn(
        "Validation exception: method={}, uri={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        errorCode.getCode(),
        message);

    List<Map<String, String>> errors =
        fieldErrors.stream().limit(MAX_VALIDATION_ERRORS).map(this::toValidationErrorItem).toList();

    ProblemDetail problemDetail = ProblemDetails.of(errorCode, request.getRequestURI(), message);
    problemDetail.setProperty("errors", errors);
    problemDetail.setProperty("errorCount", fieldErrors.size());
    if (fieldErrors.size() > MAX_VALIDATION_ERRORS) {
      problemDetail.setProperty("truncated", true);
    }

    return ResponseEntity.status(errorCode.getHttpStatus()).body(problemDetail);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolationException(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<ConstraintViolation<?>> violations = exception.getConstraintViolations().stream().toList();
    ValidationErrorCode errorCode =
        violations.isEmpty()
            ? ValidationErrorCode.INVALID_INPUT
            : resolveConstraintViolationCode(violations.get(0));
    String message = violations.isEmpty() ? errorCode.getMessage() : violations.get(0).getMessage();
    log.warn(
        "Constraint violation: method={}, uri={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        errorCode.getCode(),
        message);

    ProblemDetail problemDetail = ProblemDetails.of(errorCode, request.getRequestURI(), message);
    problemDetail.setProperty(
        "errors",
        violations.stream()
            .limit(MAX_VALIDATION_ERRORS)
            .map(
                violation -> {
                  ValidationErrorCode violationCode = resolveConstraintViolationCode(violation);
                  Map<String, String> item = new LinkedHashMap<>();
                  item.put("field", violation.getPropertyPath().toString());
                  item.put("code", violationCode.getCode());
                  item.put("message", violation.getMessage());
                  return item;
                })
            .toList());
    problemDetail.setProperty("errorCount", violations.size());
    if (violations.size() > MAX_VALIDATION_ERRORS) {
      problemDetail.setProperty("truncated", true);
    }

    return ResponseEntity.status(errorCode.getHttpStatus()).body(problemDetail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleInvalidRequest(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    CommonErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
    log.warn(
        "Invalid request body: method={}, uri={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        errorCode.getCode(),
        errorCode.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ProblemDetails.of(errorCode, request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    ValidationErrorCode errorCode = ValidationErrorCode.INVALID_INPUT;
    String message = "요청 파라미터 형식이 올바르지 않습니다.";
    log.warn(
        "Request parameter type mismatch: method={}, uri={}, parameter={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        exception.getName(),
        errorCode.getCode(),
        exception.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ProblemDetails.of(errorCode, request.getRequestURI(), message));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    if (isDuplicateEmailViolation(exception)) {
      if (isOAuthRequest(request)) {
        OAuthErrorCode errorCode = OAuthErrorCode.EMAIL_ALREADY_EXISTS;
        log.warn(
            "Duplicate OAuth email violation: method={}, uri={}, code={}, message={}",
            request.getMethod(),
            request.getRequestURI(),
            errorCode.getCode(),
            errorCode.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
            .body(ProblemDetails.of(errorCode, request.getRequestURI()));
      }
      AuthErrorCode errorCode = AuthErrorCode.DUPLICATE_EMAIL;
      log.warn(
          "Duplicate email violation: method={}, uri={}, code={}, message={}",
          request.getMethod(),
          request.getRequestURI(),
          errorCode.getCode(),
          errorCode.getMessage());
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(ProblemDetails.of(errorCode, request.getRequestURI()));
    }
    if (isDuplicateChildProfileNameViolation(exception)) {
      ChildProfileErrorCode errorCode = ChildProfileErrorCode.DUPLICATE_NAME;
      log.warn(
          "Duplicate child profile name violation: method={}, uri={}, code={}, message={}",
          request.getMethod(),
          request.getRequestURI(),
          errorCode.getCode(),
          errorCode.getMessage());
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(ProblemDetails.of(errorCode, request.getRequestURI()));
    }
    if (isDuplicateSocialAccountViolation(exception)) {
      SocialAccountErrorCode errorCode = SocialAccountErrorCode.ALREADY_LINKED;
      log.warn(
          "Duplicate social account violation: method={}, uri={}, code={}, message={}",
          request.getMethod(),
          request.getRequestURI(),
          errorCode.getCode(),
          errorCode.getMessage());
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(ProblemDetails.of(errorCode, request.getRequestURI()));
    }

    CommonErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    log.error(
        "Data integrity violation(unmapped): method={}, uri={}, code={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        errorCode.getCode(),
        exception.getMessage(),
        exception);
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ProblemDetails.of(errorCode, request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnhandledException(
      Exception exception, HttpServletRequest request) {
    CommonErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    log.error(
        "Unhandled exception: method={}, uri={}, message={}",
        request.getMethod(),
        request.getRequestURI(),
        exception.getMessage(),
        exception);
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ProblemDetails.of(errorCode, request.getRequestURI()));
  }

  private boolean isDuplicateEmailViolation(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String lower = message.toLowerCase();
        boolean duplicateKey =
            lower.contains("duplicate key") || lower.contains("unique constraint");
        boolean emailKey =
            lower.contains("users_email_key")
                || lower.contains("ux_users_email_lower")
                || lower.contains("lower(email)")
                || lower.contains("(email)")
                || lower.contains(" email ");
        if (duplicateKey && emailKey) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean isOAuthRequest(HttpServletRequest request) {
    return request.getRequestURI() != null && request.getRequestURI().contains("/auth/oauth/");
  }

  private boolean isDuplicateChildProfileNameViolation(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String lower = message.toLowerCase();
        boolean duplicateKey =
            lower.contains("duplicate key") || lower.contains("unique constraint");
        boolean childProfileNameKey = lower.contains("ux_child_profiles_user_active_name_lower");
        if (duplicateKey && childProfileNameKey) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean isDuplicateSocialAccountViolation(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String lower = message.toLowerCase();
        boolean duplicateKey =
            lower.contains("duplicate key") || lower.contains("unique constraint");
        boolean socialAccountKey =
            lower.contains("ux_social_accounts_provider_user")
                || lower.contains("ux_social_accounts_user_provider");
        if (duplicateKey && socialAccountKey) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private Map<String, String> toValidationErrorItem(FieldError fieldError) {
    ValidationErrorCode errorCode = ValidationErrorCode.from(fieldError);
    String message = resolveValidationMessage(fieldError, errorCode);
    Map<String, String> item = new LinkedHashMap<>();
    item.put("field", fieldError.getField());
    item.put("code", errorCode.getCode());
    item.put("message", message);
    return item;
  }

  private ValidationErrorCode resolveConstraintViolationCode(ConstraintViolation<?> violation) {
    if (violation == null || violation.getConstraintDescriptor() == null) {
      return ValidationErrorCode.INVALID_INPUT;
    }
    String validationType =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    String fieldName =
        violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
    return ValidationErrorCode.from(validationType, fieldName);
  }

  private String resolveValidationMessage(FieldError fieldError, ValidationErrorCode errorCode) {
    return fieldError.getDefaultMessage() != null
        ? fieldError.getDefaultMessage()
        : errorCode.getMessage();
  }
}
