package com.ssafy.backend.domain.auth.docs;

import com.ssafy.backend.domain.auth.dto.AuthStatusResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증 상태 API", description = "인증된 사용자 상태 확인 API")
public interface AuthStatusApiDocs {

  @Operation(
      summary = "인증 상태 확인",
      description = "JWT 인증이 필요한 엔드포인트 동작 여부를 확인합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content =
                @Content(
                    schema = @Schema(implementation = AuthStatusResponseDto.class),
                    examples =
                        @ExampleObject(value = AuthStatusSwaggerExamples.AUTH_STATUS_SUCCESS))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = AuthStatusSwaggerExamples.UNAUTHORIZED))),
        @ApiResponse(
            responseCode = "403",
            description = "접근 권한 없음",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = AuthStatusSwaggerExamples.FORBIDDEN))),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples =
                        @ExampleObject(value = AuthStatusSwaggerExamples.INTERNAL_SERVER_ERROR)))
      })
  ResponseEntity<AuthStatusResponseDto> getAuthStatus();
}
