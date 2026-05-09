package com.financialagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.domain.GeneratedRefreshToken;
import com.financialagent.auth.domain.ParsedRefreshToken;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorTest {

  private final RefreshTokenGenerator generator = new RefreshTokenGenerator(new SecureRandom());

  @Test
  void generatesUniqueTokensWithExtractableSessionAndSecret() {
    Set<String> tokens = new HashSet<>();

    for (int index = 0; index < 100; index++) {
      GeneratedRefreshToken generated = generator.generate();
      ParsedRefreshToken parsed = generator.parse(generated.token());

      assertThat(tokens.add(generated.token())).isTrue();
      assertThat(generated.token()).startsWith(generated.sessionId().toString() + ".");
      assertThat(parsed.sessionId()).isEqualTo(generated.sessionId());
      assertThat(parsed.randomSecret()).containsExactly(generated.randomSecret());
      assertThat(parsed.randomSecret()).hasSize(32);
    }
  }

  @Test
  void rejectsInvalidTokenFormatWithoutLeakingInput() {
    String invalidToken = "not-a-valid-refresh-token-with-secret-material";

    assertThatThrownBy(() -> generator.parse(invalidToken))
        .isInstanceOf(ServiceException.class)
        .hasMessage("Invalid refresh token format")
        .message()
        .doesNotContain(invalidToken);
  }

  @Test
  void rejectsInvalidSecretLengthSafely() {
    String tokenWithShortSecret = "7a399c21-7cf4-4387-9535-1ac8a7024ab7.c2hvcnQ";

    assertThatThrownBy(() -> generator.parse(tokenWithShortSecret))
        .isInstanceOf(ServiceException.class)
        .hasMessage("Invalid refresh token format");
  }
}
