package com.financialagent.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record MessageCreateRequest(
    @NotBlank @Size(max = 20000) String content, Map<String, Object> metadata) {}
