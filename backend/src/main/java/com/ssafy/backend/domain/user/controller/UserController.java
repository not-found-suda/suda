package com.ssafy.backend.domain.user.controller;

import com.ssafy.backend.domain.user.docs.UserApiDocs;
import com.ssafy.backend.domain.user.dto.UserResponseDto;
import com.ssafy.backend.domain.user.service.UserService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.exception.CommonErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserApiDocs {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  @Override
  public ResponseEntity<UserResponseDto> me(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    UserResponseDto responseDto = userService.getUserById(userId);
    return ResponseEntity.ok(responseDto);
  }
}
