package com.financialagent.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthConfigTest {

  @Test
  void passwordEncoderUsesBcryptCostTwelve() {
    PasswordEncoder encoder = new AuthConfig().passwordEncoder();

    String encoded = encoder.encode("Strong1!");

    assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    assertThat(encoded).startsWith("$2a$12$");
    assertThat(encoder.matches("Strong1!", encoded)).isTrue();
  }
}
