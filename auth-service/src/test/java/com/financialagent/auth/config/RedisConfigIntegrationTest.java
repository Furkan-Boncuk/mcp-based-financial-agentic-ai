package com.financialagent.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
      "management.endpoint.health.show-components=always"
    })
class RedisConfigIntegrationTest {

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @Autowired private RedisTemplate<String, String> redisTemplate;

  @Autowired private HealthEndpoint healthEndpoint;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "");
    registry.add("spring.data.redis.timeout", () -> "2s");
    registry.add("spring.data.redis.connect-timeout", () -> "2s");
  }

  @Test
  void redisTemplateConnectsToRedisContainer() {
    redisTemplate.opsForValue().set("auth:test:redis", "available");

    assertThat(redisTemplate.opsForValue().get("auth:test:redis")).isEqualTo("available");
  }

  @Test
  void healthEndpointIncludesRedisStatus() {
    HealthComponent redisHealth = healthEndpoint.healthForPath("redis");

    assertThat(redisHealth).isNotNull();
    assertThat(redisHealth.getStatus()).isEqualTo(Status.UP);
  }
}
