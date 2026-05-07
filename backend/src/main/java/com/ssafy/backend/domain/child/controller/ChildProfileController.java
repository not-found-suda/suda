package com.ssafy.backend.domain.child.controller;

import com.ssafy.backend.domain.child.docs.ChildProfileApiDocs;
import com.ssafy.backend.domain.child.dto.ChildProfileCreateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileCreateResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileListResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileResponseDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateRequestDto;
import com.ssafy.backend.domain.child.dto.ChildProfileUpdateResponseDto;
import com.ssafy.backend.domain.child.service.ChildProfileService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/children")
public class ChildProfileController implements ChildProfileApiDocs {

  private final ChildProfileService childProfileService;

  public ChildProfileController(ChildProfileService childProfileService) {
    this.childProfileService = childProfileService;
  }

  @GetMapping
  @Override
  public ResponseEntity<ChildProfileListResponseDto> getChildren(
      Authentication authentication,
      @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
    ChildProfileListResponseDto responseDto =
        childProfileService.getChildren(resolveUserId(authentication), includeInactive);
    return ResponseEntity.ok(responseDto);
  }

  @PostMapping
  @Override
  public ResponseEntity<ChildProfileCreateResponseDto> createChild(
      Authentication authentication, @Valid @RequestBody ChildProfileCreateRequestDto requestDto) {
    ChildProfileCreateResponseDto responseDto =
        childProfileService.createChild(resolveUserId(authentication), requestDto);
    return ResponseEntity.created(URI.create("/api/v1/children/" + responseDto.childId()))
        .body(responseDto);
  }

  @GetMapping("/{childId}")
  @Override
  public ResponseEntity<ChildProfileResponseDto> getChild(
      Authentication authentication, @PathVariable Long childId) {
    ChildProfileResponseDto responseDto =
        childProfileService.getChild(resolveUserId(authentication), childId);
    return ResponseEntity.ok(responseDto);
  }

  @PatchMapping("/{childId}")
  @Override
  public ResponseEntity<ChildProfileUpdateResponseDto> updateChild(
      Authentication authentication,
      @PathVariable Long childId,
      @Valid @RequestBody ChildProfileUpdateRequestDto requestDto) {
    ChildProfileUpdateResponseDto responseDto =
        childProfileService.updateChild(resolveUserId(authentication), childId, requestDto);
    return ResponseEntity.ok(responseDto);
  }

  @DeleteMapping("/{childId}")
  @Override
  public ResponseEntity<Void> deleteChild(
      Authentication authentication, @PathVariable Long childId) {
    childProfileService.deleteChild(resolveUserId(authentication), childId);
    return ResponseEntity.noContent().build();
  }

  private Long resolveUserId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
