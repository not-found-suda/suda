package com.ssafy.backend.domain.learn.dto.response;

// (카테고리 + 난이도) 단어장 화면
public record LearnWordResponse(
    Long wordId, String word, String displayText, String imageUrl, String audioUrl) {}
