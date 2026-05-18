package com.ssafy.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.child.dto.ChildProfileCreateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileCreateResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateResponseDto;
import com.ssafy.backend.domain.child.entity.ChildProfile;
import com.ssafy.backend.domain.child.exception.ChildProfileErrorCode;
import com.ssafy.backend.domain.child.repository.ChildProfileRepository;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.ValidationErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildProfileServiceValidationTest {

  private static final Long USER_ID = 1L;
  private static final Long CHILD_ID = 10L;
  private static final String DEFAULT_AVATAR_KEY = "purple_diamond";

  @Mock private ChildProfileRepository childProfileRepository;
  @Mock private UserRepository userRepository;

  private ChildProfileService childProfileService;

  @BeforeEach
  void setUp() {
    childProfileService = new ChildProfileService(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 요청 본문이 없으면 실패한다")
  void createChildRejectsNullRequest() {
    assertBusinessError(
        () -> childProfileService.createChild(USER_ID, null), ValidationErrorCode.INVALID_INPUT);
    verifyNoInteractions(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 이름이 없거나 공백이면 실패한다")
  void createChildRejectsBlankName() {
    assertBusinessError(
        () -> childProfileService.createChild(USER_ID, newCreateRequest(null, validBirthDate())),
        ChildProfileErrorCode.INVALID_NAME);
    assertBusinessError(
        () -> childProfileService.createChild(USER_ID, newCreateRequest("   ", validBirthDate())),
        ChildProfileErrorCode.INVALID_NAME);
    verifyNoInteractions(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 이름이 20자를 초과하면 실패한다")
  void createChildRejectsTooLongName() {
    String tooLongName = "가".repeat(21);

    assertBusinessError(
        () ->
            childProfileService.createChild(
                USER_ID, newCreateRequest(tooLongName, validBirthDate())),
        ChildProfileErrorCode.INVALID_NAME);
    verifyNoInteractions(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 생년월일이 없거나 미래이면 실패한다")
  void createChildRejectsInvalidBirthDate() {
    assertBusinessError(
        () -> childProfileService.createChild(USER_ID, newCreateRequest("민준", null)),
        ChildProfileErrorCode.INVALID_BIRTH_DATE);
    assertBusinessError(
        () ->
            childProfileService.createChild(
                USER_ID, newCreateRequest("민준", LocalDate.now().plusDays(1))),
        ChildProfileErrorCode.INVALID_BIRTH_DATE);
    verifyNoInteractions(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 18세를 초과하면 실패한다")
  void createChildRejectsOlderThanEighteen() {
    LocalDate olderThanEighteen = LocalDate.now().minusYears(19);

    assertBusinessError(
        () -> childProfileService.createChild(USER_ID, newCreateRequest("민준", olderThanEighteen)),
        ChildProfileErrorCode.INVALID_BIRTH_DATE);
    verifyNoInteractions(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 정확히 18세이면 성공한다")
  void createChildAllowsAgeEighteen() {
    User user = User.create("guardian@example.com", "encoded-password", "보호자");
    LocalDate birthDate = LocalDate.now().minusYears(18);
    when(childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, "민준"))
        .thenReturn(false);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(childProfileRepository.saveAndFlush(org.mockito.Mockito.any(ChildProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ChildProfileCreateResponseDto response =
        childProfileService.createChild(USER_ID, newCreateRequest("민준", birthDate));

    assertThat(response.birthDate()).isEqualTo(birthDate);
    assertThat(response.age()).isEqualTo(18);
    assertThat(response.active()).isTrue();
  }

  @Test
  @DisplayName("아이 프로필 생성 시 같은 보호자의 활성 아이 이름이 중복되면 실패한다")
  void createChildRejectsDuplicateActiveName() {
    when(childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, "민준"))
        .thenReturn(true);

    assertBusinessError(
        () -> childProfileService.createChild(USER_ID, newCreateRequest(" 민준 ", validBirthDate())),
        ChildProfileErrorCode.DUPLICATE_NAME);
    verify(childProfileRepository).existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, "민준");
    verify(childProfileRepository, never()).saveAndFlush(org.mockito.Mockito.any());
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("아이 프로필 생성 시 이름을 trim하여 저장하고 응답 나이를 계산한다")
  void createChildTrimsNameAndCalculatesAge() {
    User user = User.create("guardian@example.com", "encoded-password", "보호자");
    LocalDate birthDate = LocalDate.now().minusYears(6);
    when(childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, "민준"))
        .thenReturn(false);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(childProfileRepository.saveAndFlush(org.mockito.Mockito.any(ChildProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ChildProfileCreateResponseDto response =
        childProfileService.createChild(USER_ID, newCreateRequest(" 민준 ", birthDate));

    ArgumentCaptor<ChildProfile> captor = ArgumentCaptor.forClass(ChildProfile.class);
    verify(childProfileRepository).saveAndFlush(captor.capture());
    verify(childProfileRepository).existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, "민준");
    assertThat(captor.getValue().getName()).isEqualTo("민준");
    assertThat(response.name()).isEqualTo("민준");
    assertThat(response.birthDate()).isEqualTo(birthDate);
    assertThat(response.age()).isEqualTo(6);
    assertThat(response.active()).isTrue();
  }

  @Test
  @DisplayName("아이 프로필 수정 시 요청 본문이 없거나 수정 필드가 없으면 실패한다")
  void updateChildRejectsEmptyRequest() {
    assertBusinessError(
        () -> childProfileService.updateChild(USER_ID, CHILD_ID, null),
        ValidationErrorCode.INVALID_INPUT);
    assertBusinessError(
        () ->
            childProfileService.updateChild(
                USER_ID, CHILD_ID, new ChildProfileUpdateRequestDto(null, null, null)),
        ValidationErrorCode.INVALID_INPUT);
    verifyNoInteractions(childProfileRepository, userRepository);
  }

  @Test
  @DisplayName("아이 프로필 수정 시 이름이 유효하지 않으면 실패한다")
  void updateChildRejectsInvalidName() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));

    assertBusinessError(
        () ->
            childProfileService.updateChild(
                USER_ID, CHILD_ID, new ChildProfileUpdateRequestDto("   ", null, null)),
        ChildProfileErrorCode.INVALID_NAME);
    verify(childProfileRepository, never()).flush();
  }

  @Test
  @DisplayName("아이 프로필 수정 시 다른 활성 아이와 이름이 중복되면 실패한다")
  void updateChildRejectsDuplicateActiveName() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));
    when(childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
            USER_ID, "서연", CHILD_ID))
        .thenReturn(true);

    assertBusinessError(
        () ->
            childProfileService.updateChild(
                USER_ID, CHILD_ID, new ChildProfileUpdateRequestDto(" 서연 ", null, null)),
        ChildProfileErrorCode.DUPLICATE_NAME);
    verify(childProfileRepository)
        .existsByUserIdAndNameIgnoreCaseAndActiveTrueAndIdNot(USER_ID, "서연", CHILD_ID);
    verify(childProfileRepository, never()).flush();
  }

  @Test
  @DisplayName("아이 프로필 수정 시 미래 생년월일이면 실패한다")
  void updateChildRejectsFutureBirthDate() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));

    assertBusinessError(
        () ->
            childProfileService.updateChild(
                USER_ID,
                CHILD_ID,
                new ChildProfileUpdateRequestDto(null, LocalDate.now().plusDays(1), null)),
        ChildProfileErrorCode.INVALID_BIRTH_DATE);
    verify(childProfileRepository, never()).flush();
  }

  @Test
  @DisplayName("아이 프로필 수정 시 18세를 초과하면 실패한다")
  void updateChildRejectsOlderThanEighteenBirthDate() {
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(activeChild()));

    assertBusinessError(
        () ->
            childProfileService.updateChild(
                USER_ID,
                CHILD_ID,
                new ChildProfileUpdateRequestDto(null, LocalDate.now().minusYears(19), null)),
        ChildProfileErrorCode.INVALID_BIRTH_DATE);
    verify(childProfileRepository, never()).flush();
  }

  @Test
  @DisplayName("아이 프로필 수정 시 이름과 생년월일을 검증한 뒤 반영한다")
  void updateChildAppliesValidatedFields() {
    ChildProfile child = activeChild();
    LocalDate newBirthDate = LocalDate.now().minusYears(5);
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(child));
    when(childProfileRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
            USER_ID, "서연", CHILD_ID))
        .thenReturn(false);

    ChildProfileUpdateResponseDto response =
        childProfileService.updateChild(
            USER_ID, CHILD_ID, new ChildProfileUpdateRequestDto(" 서연 ", newBirthDate, null));

    assertThat(child.getName()).isEqualTo("서연");
    assertThat(child.getBirthDate()).isEqualTo(newBirthDate);
    assertThat(response.name()).isEqualTo("서연");
    assertThat(response.age()).isEqualTo(5);
    verify(childProfileRepository).flush();
  }

  @Test
  @DisplayName("아이 프로필 삭제 시 active=false로 변경하고 flush한다")
  void deleteChildDeactivatesChild() {
    ChildProfile child = activeChild();
    when(childProfileRepository.findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID))
        .thenReturn(Optional.of(child));

    childProfileService.deleteChild(USER_ID, CHILD_ID);

    assertThat(child.isActive()).isFalse();
    verify(childProfileRepository).findByIdAndUserIdAndActiveTrue(CHILD_ID, USER_ID);
    verify(childProfileRepository).flush();
  }

  private ChildProfileCreateRequestDto newCreateRequest(String name, LocalDate birthDate) {
    return new ChildProfileCreateRequestDto(name, birthDate, DEFAULT_AVATAR_KEY);
  }

  private ChildProfile activeChild() {
    return ChildProfile.create(
        User.create("guardian@example.com", "encoded-password", "보호자"),
        "민준",
        validBirthDate(),
        DEFAULT_AVATAR_KEY);
  }

  private LocalDate validBirthDate() {
    return LocalDate.now().minusYears(6);
  }

  private void assertBusinessError(ThrowingCall call, Object expectedErrorCode) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
