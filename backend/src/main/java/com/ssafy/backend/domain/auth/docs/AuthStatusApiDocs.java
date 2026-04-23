package com.ssafy.backend.domain.auth.docs;

import com.ssafy.backend.domain.auth.dto.AuthStatusResponseDto;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증 상태 API", description = "인증된 사용자 상태 확인 API")
public interface AuthStatusApiDocs {

  @Operation(
      summary = "인증 상태 확인",
      description = "JWT 인증이 필요한 엔드포인트 동작 여부를 확인합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "COMMON_FORBIDDEN", "COMMON_INTERNAL_SERVER_ERROR"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = AuthStatusResponseDto.class)))
  ResponseEntity<AuthStatusResponseDto> getAuthStatus();
}
