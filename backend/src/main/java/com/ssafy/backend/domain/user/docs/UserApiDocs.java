package com.ssafy.backend.domain.user.docs;

import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "사용자 API", description = "사용자 관련 API")
public interface UserApiDocs {

  @Operation(
      summary = "내 정보 조회",
      description = "인증된 사용자의 정보를 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "USER_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = UserResponseDto.class)))
  ResponseEntity<UserResponseDto> me(@Parameter(hidden = true) Authentication authentication);
}
