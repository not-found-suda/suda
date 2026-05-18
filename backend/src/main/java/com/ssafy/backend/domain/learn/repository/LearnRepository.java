package com.ssafy.backend.domain.learn.repository;

import com.ssafy.backend.domain.learn.entity.Learn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearnRepository extends JpaRepository<Learn, Long> {

  // 단어장용: 고정 순서
  @Query(
      value =
          """
            SELECT *
            FROM learn
            WHERE category_id = :categoryId
              AND difficulty = :difficulty
              AND active = true
            ORDER BY sort_order ASC, id ASC
            LIMIT :limit
            """,
      nativeQuery = true)
  List<Learn> findWordsByCategoryAndDifficultyOrderBySortOrder(
      @Param("categoryId") Long categoryId,
      @Param("difficulty") String difficulty,
      @Param("limit") int limit);

  // 퀴즈용: 랜덤 순서
  @Query(
      value =
          """
            SELECT *
            FROM learn
            WHERE category_id = :categoryId
              AND difficulty = :difficulty
              AND active = true
            ORDER BY RANDOM()
            LIMIT :limit
            """,
      nativeQuery = true)
  List<Learn> findRandomQuizWordsByCategoryAndDifficulty(
      @Param("categoryId") Long categoryId,
      @Param("difficulty") String difficulty,
      @Param("limit") int limit);
}
