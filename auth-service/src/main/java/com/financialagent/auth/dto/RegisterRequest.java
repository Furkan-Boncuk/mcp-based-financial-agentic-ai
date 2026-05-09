package com.financialagent.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message =
                "password must contain at least one uppercase letter, one number, and one special character")
        String password,
    @NotBlank @Size(max = 255) String name) {}
