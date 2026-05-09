package com.financialagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financialagent.auth.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class TokenHasherTest {

  private final TokenHasher tokenHasher = new TokenHasher(new SecureRandom());

  @Test
  void hashesAndVerifiesRawSecret() {
    byte[] rawSecret = "refresh-token-random-secret".getBytes(StandardCharsets.UTF_8);

    String storedHash = tokenHasher.hash(rawSecret);

    assertThat(storedHash).startsWith("pbkdf2_sha256$310000$");
    assertThat(storedHash).doesNotContain("refresh-token-random-secret");
    assertThat(tokenHasher.matches(rawSecret, storedHash)).isTrue();
  }

  @Test
  void rejectsDifferentRawSecret() {
    byte[] rawSecret = "refresh-token-random-secret".getBytes(StandardCharsets.UTF_8);
    byte[] differentSecret = "different-refresh-token-secret".getBytes(StandardCharsets.UTF_8);
    String storedHash = tokenHasher.hash(rawSecret);

    assertThat(tokenHasher.matches(differentSecret, storedHash)).isFalse();
  }

  @Test
  void usesDifferentSaltForEachHash() {
    byte[] rawSecret = "refresh-token-random-secret".getBytes(StandardCharsets.UTF_8);

    String firstHash = tokenHasher.hash(rawSecret);
    String secondHash = tokenHasher.hash(rawSecret);

    assertThat(firstHash).isNotEqualTo(secondHash);
    assertThat(tokenHasher.matches(rawSecret, firstHash)).isTrue();
    assertThat(tokenHasher.matches(rawSecret, secondHash)).isTrue();
  }

  @Test
  void rejectsInvalidStoredHashWithoutLeakingSecret() {
    byte[] rawSecret = "refresh-token-random-secret".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> tokenHasher.matches(rawSecret, "invalid-hash"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("Invalid refresh token hash")
        .message()
        .doesNotContain("refresh-token-random-secret");
  }

  @Test
  void rejectsMissingRawSecretSafely() {
    assertThatThrownBy(() -> tokenHasher.hash(new byte[0]))
        .isInstanceOf(ServiceException.class)
        .hasMessage("Invalid refresh token secret");
  }
}
