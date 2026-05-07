package com.ssafy.backend.domain.learn.dto.response;

public record LearnWordResponse(
    Long wordId, String word, String displayText, String imageUrl, String audioUrl) {}
