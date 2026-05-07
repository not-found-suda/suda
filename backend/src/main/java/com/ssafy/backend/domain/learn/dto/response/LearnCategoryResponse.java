package com.ssafy.backend.domain.learn.dto.response;

public record LearnCategoryResponse(
    Long categoryId, String name, String description, String thumbnailUrl) {}
