package com.ssafy.backend.domain.learn.dto.response;

import com.ssafy.backend.domain.learn.entity.LearnDifficulty;

public record LearnLevelResponse(LearnDifficulty difficulty, String name, String description) {}
