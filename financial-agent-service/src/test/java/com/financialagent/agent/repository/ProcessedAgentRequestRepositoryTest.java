package com.financialagent.agent.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProcessedAgentRequestRepositoryTest {

  @Test
  void tryClaimReturnsTrueWhenInsertSucceeds() {
    JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    ProcessedAgentRequestRepository repository = new ProcessedAgentRequestRepository(jdbcTemplate);
    UUID eventId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    when(jdbcTemplate.update(any(String.class), eq(eventId), eq("idem"), eq(agentTaskId)))
        .thenReturn(1);

    boolean claimed = repository.tryClaim(eventId, "idem", agentTaskId);

    Assertions.assertThat(claimed).isTrue();
    verify(jdbcTemplate).update(any(String.class), eq(eventId), eq("idem"), eq(agentTaskId));
  }

  @Test
  void tryClaimReturnsFalseWhenEventAlreadyExists() {
    JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    ProcessedAgentRequestRepository repository = new ProcessedAgentRequestRepository(jdbcTemplate);
    when(jdbcTemplate.update(any(String.class), any(), any(), any())).thenReturn(0);

    Assertions.assertThat(repository.tryClaim(UUID.randomUUID(), "idem", UUID.randomUUID()))
        .isFalse();
  }
}
