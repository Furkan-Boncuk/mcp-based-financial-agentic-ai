package com.financialagent.auth.repository;

import com.financialagent.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  Optional<User> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
