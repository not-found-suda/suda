package com.ssafy.backend.domain.learn.service;

import com.ssafy.backend.domain.learn.dto.response.LearnCategoryListResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnCategoryResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnLevelListResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnLevelResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnWordListResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnWordResponse;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.repository.LearnCategoryRepository;
import com.ssafy.backend.domain.learn.repository.LearnRepository;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LearnService {

  private static final int DEFAULT_WORD_COUNT = 5;

  private final LearnCategoryRepository learnCategoryRepository;
  private final LearnRepository learnRepository;

  public LearnService(
      LearnCategoryRepository learnCategoryRepository, LearnRepository learnRepository) {
    this.learnCategoryRepository = learnCategoryRepository;
    this.learnRepository = learnRepository;
  }

  public LearnCategoryListResponse getCategories() {
    return new LearnCategoryListResponse(
        learnCategoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(
                category ->
                    new LearnCategoryResponse(
                        category.getId(),
                        category.getName(),
                        category.getDescription(),
                        category.getThumbnailUrl()))
            .toList());
  }

  public LearnLevelListResponse getLevels() {
    return new LearnLevelListResponse(
        Arrays.stream(LearnDifficulty.values())
            .map(
                difficulty ->
                    new LearnLevelResponse(
                        difficulty, difficulty.getDisplayName(), difficulty.getDescription()))
            .toList());
  }

  public LearnWordListResponse getWords(Long categoryId, LearnDifficulty difficulty) {
    return new LearnWordListResponse(
        learnRepository
            .findRandomWordsByCategoryAndDifficulty(
                categoryId, difficulty.name(), DEFAULT_WORD_COUNT)
            .stream()
            .map(
                word ->
                    new LearnWordResponse(
                        word.getId(),
                        word.getWord(),
                        word.getDisplayText(),
                        word.getImageUrl(),
                        word.getAudioUrl()))
            .toList());
  }
}
