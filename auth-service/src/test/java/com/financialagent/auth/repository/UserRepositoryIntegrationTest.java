package com.financialagent.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialagent.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@ImportAutoConfiguration(
    exclude = {RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class})
class UserRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("auth_repository_test")
          .withUsername("postgres")
          .withPassword("postgres");

  @Autowired private UserRepository userRepository;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    registry.add("spring.flyway.schemas", () -> "auth_service");
    registry.add("spring.flyway.create-schemas", () -> "true");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "auth_service");
    registry.add("auth.jwt.private-key", com.financialagent.auth.config.TestJwtKeys::privateKey);
    registry.add("auth.jwt.public-key", com.financialagent.auth.config.TestJwtKeys::publicKey);
  }

  @Test
  void findsActiveUserByEmailIgnoringCase() {
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "user@example.com", "User", "hash", "free");
    userRepository.saveAndFlush(user);

    Optional<User> result =
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("USER@example.com");

    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(userId);
    assertThat(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("USER@example.com"))
        .isTrue();
  }
}
