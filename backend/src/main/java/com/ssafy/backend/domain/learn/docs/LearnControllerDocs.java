package com.ssafy.backend.domain.learn.docs;

import com.ssafy.backend.domain.learn.dto.response.LearnCategoryResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnLevelResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnWordResponse;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.global.docs.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "학습 API", description = "카테고리 및 단어장 학습 API")
public interface LearnControllerDocs {

  @Operation(
      summary = "학습 카테고리 목록 조회",
      description = "학습 가능한 카테고리 목록을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  List<LearnCategoryResponse> getCategories();

  @Operation(
      summary = "난이도 목록 조회",
      description = "학습 가능한 난이도 목록을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  List<LearnLevelResponse> getLevels();

  @Operation(
      summary = "카테고리/난이도별 단어 목록 조회",
      description = "선택한 카테고리와 난이도에 해당하는 단어 목록을 조회합니다.",
      security = {@SecurityRequirement(name = "bearerAuth")})
  @ApiErrorCodes({"COMMON_UNAUTHORIZED"})
  List<LearnWordResponse> getWords(
      @RequestParam Long categoryId, @RequestParam LearnDifficulty difficulty);
}
