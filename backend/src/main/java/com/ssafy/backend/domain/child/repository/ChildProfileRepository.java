package com.ssafy.backend.domain.child.repository;

import com.ssafy.backend.domain.child.entity.ChildProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {

  List<ChildProfile> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

  List<ChildProfile> findByUserIdAndActiveTrueOrderByCreatedAtAscIdAsc(Long userId);

  Optional<ChildProfile> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

  boolean existsByUserIdAndNameIgnoreCaseAndActiveTrue(Long userId, String name);

  boolean existsByUserIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
      Long userId, String name, Long childId);
}
