package io.inertia.redis;

import io.inertia.core.SessionStore;
import java.time.Duration;

/**
 * Bounds for one Redis delivery domain. Time uses Redis server time, not JVM clocks.
 *
 * @param namespace isolated application identifier
 * @param idleTtl idle lifetime refreshed only by successful CAS operations, at most one day
 * @param lease reservation lifetime, shorter than the idle lifetime
 * @param terminalRetention minimum lifetime of terminal token records, at most the idle lifetime
 * @param maxBytes maximum UTF-8 JSON envelope size
 * @param maxReservations maximum concurrent reserved deliveries
 * @param maxTerminals maximum retained terminal records; exhaustion fails closed
 * @param maxAttempts bounded explicit CAS conflict/recalculation attempts
 */
public record RedisSessionOptions(
    String namespace,
    Duration idleTtl,
    Duration lease,
    Duration terminalRetention,
    int maxBytes,
    int maxReservations,
    int maxTerminals,
    int maxAttempts) {
  /**
   * Validates capacity and millisecond durations.
   *
   * @throws IllegalArgumentException if any bound is invalid
   */
  public RedisSessionOptions {
    SessionStore.requireNamespace(namespace);
    if (idleTtl == null
        || lease == null
        || terminalRetention == null
        || idleTtl.compareTo(Duration.ofDays(1)) > 0
        || idleTtl.toMillis() < 2
        || lease.compareTo(idleTtl) >= 0
        || lease.toMillis() < 1
        || terminalRetention.compareTo(idleTtl) > 0
        || terminalRetention.toMillis() < 1
        || maxBytes < 1024
        || maxBytes > 16 * 1024 * 1024
        || maxReservations < 1
        || maxReservations > 1024
        || maxTerminals < maxReservations
        || maxTerminals > 100000
        || maxAttempts < 1
        || maxAttempts > 100) throw new IllegalArgumentException("Invalid Redis delivery bounds");
  }

  /**
   * Creates conservative standalone defaults; applications must size them for their request budget.
   *
   * @param namespace application isolation namespace
   * @return 30-minute idle domain, 30-second lease, 5-minute terminal retention and 1MiB envelope
   */
  public static RedisSessionOptions defaults(String namespace) {
    return new RedisSessionOptions(
        namespace,
        Duration.ofMinutes(30),
        Duration.ofSeconds(30),
        Duration.ofMinutes(5),
        1024 * 1024,
        16,
        4096,
        16);
  }
}
