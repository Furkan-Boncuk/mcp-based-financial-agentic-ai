package com.financialagent.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialagent.auth.domain.GeneratedAccessToken;
import com.financialagent.auth.domain.User;
import com.financialagent.auth.dto.AuthResponse;
import com.financialagent.auth.dto.UserProfileResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserMapperTest {

  private final UserMapper mapper = new UserMapper();

  @Test
  void mapsUserToAuthResponseDto() {
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "user@example.com", "User", "hash", "free");
    GeneratedAccessToken token =
        new GeneratedAccessToken(
            "jwt-access-token", 900, Instant.now().plusSeconds(900), UUID.randomUUID());

    AuthResponse response = mapper.toAuthResponse(token, user);

    assertThat(response.accessToken()).isEqualTo("jwt-access-token");
    assertThat(response.expiresIn()).isEqualTo(900);
    assertThat(response.userId()).isEqualTo(userId);
  }

  @Test
  void mapsUserToProfileResponseDtoWithoutPasswordHash() {
    UUID userId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-05-09T10:00:00Z");
    User user = new User(userId, "user@example.com", "User", "hash", "premium");
    ReflectionTestUtils.setField(user, "createdAt", createdAt);

    UserProfileResponse response = mapper.toProfileResponse(user);

    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.email()).isEqualTo("user@example.com");
    assertThat(response.name()).isEqualTo("User");
    assertThat(response.tier()).isEqualTo("premium");
    assertThat(response.createdAt()).isEqualTo(createdAt);
    assertThat(response.toString()).doesNotContain("hash");
  }
}
