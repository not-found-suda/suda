package com.ssafy.backend.domain.learn.controller;

import com.ssafy.backend.domain.learn.docs.LearnControllerDocs;
import com.ssafy.backend.domain.learn.dto.response.LearnCategoryResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnLevelResponse;
import com.ssafy.backend.domain.learn.dto.response.LearnWordResponse;
import com.ssafy.backend.domain.learn.entity.LearnDifficulty;
import com.ssafy.backend.domain.learn.service.LearnService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learn")
public class LearnController implements LearnControllerDocs {

  private final LearnService learnService;

  public LearnController(LearnService learnService) {
    this.learnService = learnService;
  }

  @GetMapping("/categories")
  public List<LearnCategoryResponse> getCategories() {
    return learnService.getCategories();
  }

  @GetMapping("/levels")
  public List<LearnLevelResponse> getLevels() {
    return learnService.getLevels();
  }

  @GetMapping("/words")
  public List<LearnWordResponse> getWords(
      @RequestParam Long categoryId, @RequestParam LearnDifficulty difficulty) {
    return learnService.getWords(categoryId, difficulty);
  }
}
