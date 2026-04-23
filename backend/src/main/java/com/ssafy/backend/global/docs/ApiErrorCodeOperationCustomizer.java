package com.ssafy.backend.global.docs;

import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.ProblemDetailConventions;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

@Component
public class ApiErrorCodeOperationCustomizer implements OperationCustomizer {

  private static final Logger log = LoggerFactory.getLogger(ApiErrorCodeOperationCustomizer.class);
  private static final String PROBLEM_JSON_MEDIA_TYPE = "application/problem+json";
  private static final String ERROR_TYPE_PREFIX = "urn:s14p31a404:error:";

  private final ApiErrorCodeCatalog apiErrorCodeCatalog;

  public ApiErrorCodeOperationCustomizer(ApiErrorCodeCatalog apiErrorCodeCatalog) {
    this.apiErrorCodeCatalog = apiErrorCodeCatalog;
  }

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    Set<String> declaredCodes = resolveDeclaredCodes(handlerMethod);
    if (declaredCodes.isEmpty()) {
      return operation;
    }

    Map<Integer, List<ErrorCode>> groupedErrorCodes =
        groupErrorCodesByStatus(declaredCodes, handlerMethod);
    if (groupedErrorCodes.isEmpty()) {
      return operation;
    }
    String instanceTemplate = resolveInstanceTemplate(handlerMethod);

    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }

    for (Map.Entry<Integer, List<ErrorCode>> entry : groupedErrorCodes.entrySet()) {
      applyProblemResponse(responses, entry.getKey(), entry.getValue(), instanceTemplate);
    }
    return operation;
  }

  private void applyProblemResponse(
      ApiResponses responses, int statusCode, List<ErrorCode> errorCodes, String instanceTemplate) {
    String responseCode = String.valueOf(statusCode);
    ApiResponse apiResponse = responses.computeIfAbsent(responseCode, ignored -> new ApiResponse());

    if (apiResponse.getDescription() == null || apiResponse.getDescription().isBlank()) {
      apiResponse.setDescription("실패 응답");
    }

    Content content = apiResponse.getContent();
    if (content == null) {
      content = new Content();
      apiResponse.setContent(content);
    }

    MediaType mediaType = content.get(PROBLEM_JSON_MEDIA_TYPE);
    if (mediaType == null) {
      mediaType = new MediaType();
      content.addMediaType(PROBLEM_JSON_MEDIA_TYPE, mediaType);
    }

    if (mediaType.getSchema() == null) {
      mediaType.setSchema(problemDetailSchema());
    }

    Map<String, Example> examples = mediaType.getExamples();
    if (examples == null) {
      examples = new LinkedHashMap<>();
      mediaType.setExamples(examples);
    }

    for (ErrorCode errorCode : errorCodes) {
      examples.putIfAbsent(errorCode.getCode(), buildExample(errorCode, instanceTemplate));
    }
  }

  private Map<Integer, List<ErrorCode>> groupErrorCodesByStatus(
      Set<String> declaredCodes, HandlerMethod handlerMethod) {
    Map<Integer, List<ErrorCode>> grouped = new LinkedHashMap<>();
    for (String code : declaredCodes) {
      ErrorCode errorCode =
          apiErrorCodeCatalog
              .findByCode(code)
              .orElseGet(
                  () -> {
                    log.warn(
                        "Unknown @ApiErrorCodes value '{}' at {}#{}",
                        code,
                        handlerMethod.getBeanType().getSimpleName(),
                        handlerMethod.getMethod().getName());
                    return null;
                  });
      if (errorCode == null) {
        continue;
      }
      int status = errorCode.getHttpStatus().value();
      grouped.computeIfAbsent(status, ignored -> new ArrayList<>()).add(errorCode);
    }
    return grouped;
  }

  private Set<String> resolveDeclaredCodes(HandlerMethod handlerMethod) {
    Set<String> codes = new LinkedHashSet<>();
    collectCodes(handlerMethod.getMethod(), codes);
    collectCodes(handlerMethod.getBeanType(), codes);
    for (Method interfaceMethod : resolveInterfaceMethods(handlerMethod)) {
      collectCodes(interfaceMethod, codes);
    }
    return codes;
  }

  private void collectCodes(AnnotatedElement element, Set<String> accumulator) {
    ApiErrorCodes annotation =
        AnnotatedElementUtils.findMergedAnnotation(element, ApiErrorCodes.class);
    if (annotation == null) {
      return;
    }
    Arrays.stream(annotation.value())
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .forEach(accumulator::add);
  }

  private List<Method> resolveInterfaceMethods(HandlerMethod handlerMethod) {
    Class<?> beanType = handlerMethod.getBeanType();
    Method method = handlerMethod.getMethod();
    List<Method> methods = new ArrayList<>();
    for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClass(beanType)) {
      try {
        methods.add(interfaceType.getMethod(method.getName(), method.getParameterTypes()));
      } catch (NoSuchMethodException ignored) {
        // Ignore unrelated interface methods.
      }
    }
    return methods;
  }

  private String resolveInstanceTemplate(HandlerMethod handlerMethod) {
    String classPath = resolveFirstPath(handlerMethod.getBeanType());
    String methodPath = resolveFirstPath(handlerMethod.getMethod());

    if (classPath.isBlank() && methodPath.isBlank()) {
      return "/{request-path}";
    }
    String mergedPath;
    if (classPath.isBlank()) {
      mergedPath = methodPath;
    } else if (methodPath.isBlank()) {
      mergedPath = classPath;
    } else if (classPath.endsWith("/") && methodPath.startsWith("/")) {
      mergedPath = classPath.substring(0, classPath.length() - 1) + methodPath;
    } else if (!classPath.endsWith("/") && !methodPath.startsWith("/")) {
      mergedPath = classPath + "/" + methodPath;
    } else {
      mergedPath = classPath + methodPath;
    }
    return ProblemDetailConventions.normalizeInstance(mergedPath);
  }

  private String resolveFirstPath(AnnotatedElement element) {
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
    if (mapping == null) {
      return "";
    }
    if (mapping.path().length > 0 && mapping.path()[0] != null && !mapping.path()[0].isBlank()) {
      return mapping.path()[0].trim();
    }
    if (mapping.value().length > 0 && mapping.value()[0] != null && !mapping.value()[0].isBlank()) {
      return mapping.value()[0].trim();
    }
    return "";
  }

  private static Schema<?> problemDetailSchema() {
    return new ObjectSchema()
        .addProperty("type", new StringSchema().format("uri"))
        .addProperty("title", new StringSchema())
        .addProperty("status", new IntegerSchema())
        .addProperty("detail", new StringSchema())
        .addProperty("instance", new StringSchema().format("uri"))
        .addProperty("code", new StringSchema())
        .addProperty("traceId", new StringSchema());
  }

  private static Example buildExample(ErrorCode errorCode, String instanceTemplate) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("type", ERROR_TYPE_PREFIX + toKebabCase(errorCode.getCode()));
    value.put("title", ProblemDetailConventions.resolveTitle(errorCode));
    value.put("status", errorCode.getHttpStatus().value());
    value.put("detail", errorCode.getMessage());
    value.put("instance", ProblemDetailConventions.normalizeInstance(instanceTemplate));
    value.put("code", errorCode.getCode());
    value.put("traceId", "9f8d7c6b5a4e3210");

    Example example = new Example();
    example.setSummary(errorCode.getCode());
    example.setValue(value);
    return example;
  }

  private static String toKebabCase(String value) {
    return value.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
