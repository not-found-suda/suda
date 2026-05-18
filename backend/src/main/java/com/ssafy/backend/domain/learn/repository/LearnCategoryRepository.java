package com.ssafy.backend.domain.learn.repository;

import com.ssafy.backend.domain.learn.entity.LearnCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnCategoryRepository extends JpaRepository<LearnCategory, Long> {

  List<LearnCategory> findByActiveTrueOrderBySortOrderAsc();
}
