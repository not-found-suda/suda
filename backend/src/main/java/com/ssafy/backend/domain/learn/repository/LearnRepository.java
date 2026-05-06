package com.ssafy.backend.domain.learn.repository;

import com.ssafy.backend.domain.learn.entity.Learn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearnRepository extends JpaRepository<Learn, Long> {

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
  List<Learn> findRandomWordsByCategoryAndDifficulty(
      @Param("categoryId") Long categoryId,
      @Param("difficulty") String difficulty,
      @Param("limit") int limit);
}
