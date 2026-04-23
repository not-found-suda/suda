package com.ssafy.backend.global.docs;

import com.ssafy.backend.global.dto.HealthResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "헬스체크 API", description = "서비스 상태 확인 API")
public interface HealthApiDocs {

  @Operation(summary = "헬스체크", description = "백엔드 서비스 상태를 확인합니다.")
  @ApiErrorCodes({"COMMON_INTERNAL_SERVER_ERROR"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = HealthResponseDto.class)))
  ResponseEntity<HealthResponseDto> health();
}
