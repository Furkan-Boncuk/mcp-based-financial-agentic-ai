package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.AgentProgressEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProgressEventRepository extends JpaRepository<AgentProgressEvent, UUID> {}
