package com.financialagent.auth.repository;

import com.financialagent.auth.domain.AuthAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, UUID> {}
