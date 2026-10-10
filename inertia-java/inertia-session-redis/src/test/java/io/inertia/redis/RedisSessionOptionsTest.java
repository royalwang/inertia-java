package io.inertia.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisSessionOptionsTest {
  @Test
  void rejectsUnsafeNamespacesAndUnboundedBudgets() {
    assertThrows(IllegalArgumentException.class, () -> RedisSessionOptions.defaults("a{b}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RedisSessionOptions(
                "safe",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1024,
                1,
                2,
                2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RedisSessionOptions(
                "safe",
                Duration.ofDays(2),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1024,
                1,
                2,
                2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RedisSessionBackend(
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration(),
                Duration.ofSeconds(6),
                4));
  }
}
