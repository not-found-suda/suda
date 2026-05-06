package com.ssafy.backend.domain.learn.dto.response;

// 카테고리 목록 화면용
public record LearnCategoryResponse(
    Long id, String name, String description, String thumbnailUrl) {}
