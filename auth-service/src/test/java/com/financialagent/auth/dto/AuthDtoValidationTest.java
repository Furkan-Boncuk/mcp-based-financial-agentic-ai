package com.financialagent.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthDtoValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void registerRequestRequiresStrongPassword() {
    RegisterRequest request = new RegisterRequest("user@example.com", "weakpass", "User");

    Set<?> violations = validator.validate(request);

    assertThat(violations.toString()).contains("password");
  }

  @Test
  void registerRequestAcceptsValidInput() {
    RegisterRequest request = new RegisterRequest("user@example.com", "Strong1!", "User");

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void loginRequestRequiresValidEmailAndPassword() {
    LoginRequest request = new LoginRequest("not-an-email", "");

    Set<?> violations = validator.validate(request);

    assertThat(violations.toString()).contains("email");
    assertThat(violations.toString()).contains("password");
  }
}
