package com.ssafy.backend.domain.child.docs;

import com.ssafy.backend.domain.child.dto.ChildProfileCreateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileCreateResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileListResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateResponseDto;
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

@Tag(name = "아이 프로필 API", description = "아이 프로필 조회, 생성, 수정, 삭제 API")
public interface ChildProfileApiDocs {

  @Operation(
      summary = "아이 프로필 목록 조회",
      description = "현재 로그인한 보호자가 등록한 아이 프로필 목록을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ChildProfileListResponseDto.class)))
  ResponseEntity<ChildProfileListResponseDto> getChildren(
      @Parameter(hidden = true) Authentication authentication, boolean includeInactive);

  @Operation(
      summary = "아이 프로필 생성",
      description = "현재 로그인한 보호자의 아이 프로필을 생성합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "CHILD_PROFILE_INVALID_NAME",
    "CHILD_PROFILE_INVALID_BIRTH_DATE",
    "CHILD_PROFILE_DUPLICATE_NAME",
    "COMMON_UNAUTHORIZED"
  })
  @ApiResponse(
      responseCode = "201",
      description = "생성됨",
      content = @Content(schema = @Schema(implementation = ChildProfileCreateResponseDto.class)))
  ResponseEntity<ChildProfileCreateResponseDto> createChild(
      @Parameter(hidden = true) Authentication authentication,
      ChildProfileCreateRequestDto request);

  @Operation(
      summary = "아이 프로필 상세 조회",
      description = "현재 로그인한 보호자의 특정 아이 프로필을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ChildProfileResponseDto.class)))
  ResponseEntity<ChildProfileResponseDto> getChild(
      @Parameter(hidden = true) Authentication authentication, Long childId);

  @Operation(
      summary = "아이 프로필 수정",
      description = "현재 로그인한 보호자의 아이 프로필을 수정합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({
    "VALIDATION_INVALID_INPUT",
    "CHILD_PROFILE_INVALID_NAME",
    "CHILD_PROFILE_INVALID_BIRTH_DATE",
    "CHILD_PROFILE_DUPLICATE_NAME",
    "COMMON_UNAUTHORIZED",
    "CHILD_PROFILE_NOT_FOUND"
  })
  @ApiResponse(
      responseCode = "200",
      description = "성공",
      content = @Content(schema = @Schema(implementation = ChildProfileUpdateResponseDto.class)))
  ResponseEntity<ChildProfileUpdateResponseDto> updateChild(
      @Parameter(hidden = true) Authentication authentication,
      Long childId,
      ChildProfileUpdateRequestDto request);

  @Operation(
      summary = "아이 프로필 삭제",
      description = "현재 로그인한 보호자의 아이 프로필을 비활성화합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED", "CHILD_PROFILE_NOT_FOUND"})
  @ApiResponse(responseCode = "204", description = "콘텐츠 없음")
  ResponseEntity<Void> deleteChild(
      @Parameter(hidden = true) Authentication authentication, Long childId);
}
