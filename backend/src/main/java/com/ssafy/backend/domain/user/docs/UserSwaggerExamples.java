package com.ssafy.backend.domain.user.docs;

public final class UserSwaggerExamples {

  private UserSwaggerExamples() {}

  public static final String ME_SUCCESS =
      """
        {
          "userId": 1,
          "email": "user1@test.com",
          "active": true,
          "role": "USER"
        }
        """;
}
