package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.AgentTaskStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {

  Optional<AgentTask> findByIdAndUserId(UUID id, UUID userId);

  Page<AgentTask> findByUserIdAndStatus(UUID userId, AgentTaskStatus status, Pageable pageable);
}
