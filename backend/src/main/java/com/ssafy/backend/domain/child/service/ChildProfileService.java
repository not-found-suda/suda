package com.ssafy.backend.domain.child.service;

import com.ssafy.backend.domain.child.dto.ChildProfileCreateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileCreateResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileListResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileSummaryResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateResponseDto;
import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChildProfileService {

  private static final int MAX_NAME_LENGTH = 20;
  private static final int MAX_AGE = 18;
  private static final String DEFAULT_AVATAR_KEY = "purple_diamond";
  private static final Set<String> ALLOWED_AVATAR_KEYS =
      Set.of(
          "purple_diamond",
          "yellow_circle",
          "blue_square",
          "green_wink_square",
          "navy_hexagon",
          "orange_pentagon",
          "pink_oval",
          "red_triangle",
          "teal_star");

  private final ChildProfileRepository childProfileRepository;
  private final UserRepository userRepository;

  public ChildProfileService(
      ChildProfileRepository childProfileRepository, UserRepository userRepository) {
    this.childProfileRepository = childProfileRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public ChildProfileListResponseDto getChildren(Long userId, boolean includeInactive) {
    List<ChildProfile> children =
        includeInactive
            ? childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId)
            : childProfileRepository.findByUserIdAndActiveTrueOrderByCreatedAtAscIdAsc(userId);
    return new ChildProfileListResponseDto(children.stream().map(this::toSummaryResponse).toList());
  }

  public ChildProfileCreateResponseDto createChild(
      Long userId, ChildProfileCreateRequestDto requestDto) {
    if (requestDto == null) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "요청 본문이 필요합니다.");
    }

    String name = validateAndNormalizeName(requestDto.name());
    LocalDate birthDate = validateBirthDate(requestDto.birthDate());
    String avatarKey = validateAndNormalizeAvatarKey(requestDto.avatarKey());

    if (childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(userId, name)) {
      throw new BusinessException(ChildProfileErrorCode.DUPLICATE_NAME);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

    ChildProfile childProfile =
        childProfileRepository.saveAndFlush(ChildProfile.create(user, name, birthDate, avatarKey));

    return toCreateResponse(childProfile);
  }

  @Transactional(readOnly = true)
  public ChildProfileResponseDto getChild(Long userId, Long childId) {
    ChildProfile childProfile = findOwnedActiveChild(userId, childId);
    return toResponse(childProfile);
  }

  public ChildProfileUpdateResponseDto updateChild(
      Long userId, Long childId, ChildProfileUpdateRequestDto requestDto) {
    if (requestDto == null
        || (requestDto.name() == null
            && requestDto.birthDate() == null
            && requestDto.avatarKey() == null)) {
      throw new BusinessException(
          ValidationErrorCode.INVALID_INPUT, "요청 본문에는 최소 1개 이상의 수정 대상 필드가 포함되어야 합니다.");
    }

    ChildProfile childProfile = findOwnedActiveChild(userId, childId);

    String name = null;
    if (requestDto.name() != null) {
      name = validateAndNormalizeName(requestDto.name());
      if (childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
          userId, name, childId)) {
        throw new BusinessException(ChildProfileErrorCode.DUPLICATE_NAME);
      }
    }

    LocalDate birthDate =
        requestDto.birthDate() == null ? null : validateBirthDate(requestDto.birthDate());

    String avatarKey =
        requestDto.avatarKey() == null
            ? null
            : validateAndNormalizeAvatarKey(requestDto.avatarKey());

    childProfile.update(name, birthDate, avatarKey);
    childProfileRepository.flush();

    return toUpdateResponse(childProfile);
  }

  public void deleteChild(Long userId, Long childId) {
    ChildProfile childProfile = findOwnedActiveChild(userId, childId);
    childProfile.deactivate();
    childProfileRepository.flush();
  }

  private ChildProfile findOwnedActiveChild(Long userId, Long childId) {
    return childProfileRepository
        .findByIdAndUserIdAndActiveTrue(childId, userId)
        .orElseThrow(() -> new BusinessException(ChildProfileErrorCode.NOT_FOUND));
  }

  private String validateAndNormalizeName(String name) {
    if (name == null) {
      throw new BusinessException(ChildProfileErrorCode.INVALID_NAME);
    }

    String normalizedName = name.trim();
    if (normalizedName.isEmpty() || normalizedName.length() > MAX_NAME_LENGTH) {
      throw new BusinessException(ChildProfileErrorCode.INVALID_NAME);
    }

    return normalizedName;
  }

  private String validateAndNormalizeAvatarKey(String avatarKey) {
    if (avatarKey == null || avatarKey.isBlank()) {
      return DEFAULT_AVATAR_KEY;
    }

    String normalizedAvatarKey = avatarKey.trim();
    if (!ALLOWED_AVATAR_KEYS.contains(normalizedAvatarKey)) {
      throw new BusinessException(ValidationErrorCode.INVALID_INPUT, "지원하지 않는 avatarKey입니다.");
    }

    return normalizedAvatarKey;
  }

  private LocalDate validateBirthDate(LocalDate birthDate) {
    if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
      throw new BusinessException(ChildProfileErrorCode.INVALID_BIRTH_DATE);
    }

    int age = calculateAge(birthDate);
    if (age < 0 || age > MAX_AGE) {
      throw new BusinessException(ChildProfileErrorCode.INVALID_BIRTH_DATE);
    }

    return birthDate;
  }

  private ChildProfileSummaryResponseDto toSummaryResponse(ChildProfile childProfile) {
    return new ChildProfileSummaryResponseDto(
        childProfile.getId(),
        childProfile.getName(),
        childProfile.getBirthDate(),
        calculateAge(childProfile.getBirthDate()),
        childProfile.getAvatarKey(),
        childProfile.isActive());
  }

  private ChildProfileResponseDto toResponse(ChildProfile childProfile) {
    return new ChildProfileResponseDto(
        childProfile.getId(),
        childProfile.getName(),
        childProfile.getBirthDate(),
        calculateAge(childProfile.getBirthDate()),
        childProfile.getAvatarKey(),
        childProfile.isActive(),
        childProfile.getCreatedAt(),
        childProfile.getUpdatedAt());
  }

  private ChildProfileCreateResponseDto toCreateResponse(ChildProfile childProfile) {
    return new ChildProfileCreateResponseDto(
        childProfile.getId(),
        childProfile.getName(),
        childProfile.getBirthDate(),
        calculateAge(childProfile.getBirthDate()),
        childProfile.getAvatarKey(),
        childProfile.isActive(),
        childProfile.getCreatedAt());
  }

  private ChildProfileUpdateResponseDto toUpdateResponse(ChildProfile childProfile) {
    return new ChildProfileUpdateResponseDto(
        childProfile.getId(),
        childProfile.getName(),
        childProfile.getBirthDate(),
        calculateAge(childProfile.getBirthDate()),
        childProfile.getAvatarKey(),
        childProfile.isActive(),
        childProfile.getUpdatedAt());
  }

  private int calculateAge(LocalDate birthDate) {
    return Period.between(birthDate, LocalDate.now()).getYears();
  }
}
