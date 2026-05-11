package com.financialagent.agent.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedAgentRequestRepository {

  private final JdbcTemplate jdbcTemplate;

  public ProcessedAgentRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean tryClaim(UUID eventId, String idempotencyKey, UUID agentTaskId) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO processed_agent_requests (event_id, idempotency_key, agent_task_id)
            VALUES (?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """,
            eventId,
            idempotencyKey,
            agentTaskId);
    return inserted == 1;
  }
}
