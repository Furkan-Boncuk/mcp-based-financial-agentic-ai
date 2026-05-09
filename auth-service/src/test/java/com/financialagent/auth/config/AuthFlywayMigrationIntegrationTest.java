package com.financialagent.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AuthFlywayMigrationIntegrationTest {

  private static final String AUTH_SCHEMA = "auth_service";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("auth_test")
          .withUsername("postgres")
          .withPassword("postgres");

  private DataSource dataSource;

  @BeforeEach
  void setUp() {
    dataSource =
        DataSourceBuilder.create()
            .url(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .driverClassName(postgres.getDriverClassName())
            .build();
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(AUTH_SCHEMA)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(AUTH_SCHEMA)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void migrationCreatesAuthTablesWithoutMessageCount() throws SQLException {
    assertThat(tableExists("users")).isTrue();
    assertThat(tableExists("roles")).isTrue();
    assertThat(tableExists("user_roles")).isTrue();
    assertThat(tableExists("auth_audit_events")).isTrue();
    assertThat(columnExists("users", "message_count")).isFalse();
  }

  @Test
  void usersEmailUniqueConstraintIsCaseInsensitive() throws SQLException {
    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO auth_service.users (id, email, name, password_hash)
          VALUES ('%s', 'user@example.com', 'User', 'hash')
          """
              .formatted(firstUserId));

      assertThatThrownBy(
              () ->
                  statement.execute(
                      """
                      INSERT INTO auth_service.users (id, email, name, password_hash)
                      VALUES ('%s', 'USER@example.com', 'Duplicate User', 'hash')
                      """
                          .formatted(secondUserId)))
          .isInstanceOf(PSQLException.class);
    }
  }

  private boolean tableExists(String tableName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        ResultSet result =
            connection
                .getMetaData()
                .getTables(null, AUTH_SCHEMA, tableName, new String[] {"TABLE"})) {
      return result.next();
    }
  }

  private boolean columnExists(String tableName, String columnName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        ResultSet result =
            connection.getMetaData().getColumns(null, AUTH_SCHEMA, tableName, columnName)) {
      return result.next();
    }
  }
}
