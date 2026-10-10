package io.inertia.boot;

import io.inertia.redis.RedisSessionOptions;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional standalone Redis delivery configuration, activated only by inertia.session.store=redis.
 * Authentication belongs to Redis, while host HttpSession sharing remains application-owned.
 *
 * @param host standalone endpoint host, default 127.0.0.1
 * @param port endpoint port, default 6379
 * @param database nonnegative Redis database, default zero
 * @param username optional ACL username
 * @param password optional Redis password; excluded from toString
 * @param commandTimeout connect/command timeout, default 200ms and at most five seconds
 * @param maxCommands concurrent command limit, default 32
 * @param idleTtl idle domain lifetime, default 30 minutes
 * @param lease delivery reservation lifetime, default 30 seconds
 * @param terminalRetention terminal token retention, default five minutes
 * @param maxBytes maximum domain JSON bytes, default 1MiB
 * @param maxReservations concurrent reservation limit, default 16
 * @param maxTerminals terminal record limit, default 4096
 * @param maxAttempts explicit conflict recalculation attempts, default 16
 */
@ConfigurationProperties("inertia.session.redis")
public record InertiaRedisProperties(
    @DefaultValue("127.0.0.1") String host,
    @DefaultValue("6379") int port,
    @DefaultValue("0") int database,
    String username,
    String password,
    @DefaultValue("200ms") Duration commandTimeout,
    @DefaultValue("32") int maxCommands,
    @DefaultValue("30m") Duration idleTtl,
    @DefaultValue("30s") Duration lease,
    @DefaultValue("5m") Duration terminalRetention,
    @DefaultValue("1048576") int maxBytes,
    @DefaultValue("16") int maxReservations,
    @DefaultValue("4096") int maxTerminals,
    @DefaultValue("16") int maxAttempts) {
  /**
   * Validates transport and storage bounds during configuration binding.
   *
   * @throws IllegalArgumentException if endpoint, time or capacity bounds are invalid
   */
  public InertiaRedisProperties {
    if (host == null
        || host.isBlank()
        || port < 1
        || port > 65535
        || database < 0
        || commandTimeout == null
        || commandTimeout.compareTo(Duration.ofSeconds(5)) > 0
        || commandTimeout.toMillis() < 1
        || maxCommands < 1
        || maxCommands > 1024)
      throw new IllegalArgumentException("Invalid Redis delivery connection settings");
    new RedisSessionOptions(
        "validation",
        idleTtl,
        lease,
        terminalRetention,
        maxBytes,
        maxReservations,
        maxTerminals,
        maxAttempts);
  }

  /**
   * Creates validated bounds in the main Inertia application namespace.
   *
   * @param namespace isolated application namespace from InertiaProperties
   * @return immutable Redis delivery bounds
   */
  public RedisSessionOptions options(String namespace) {
    return new RedisSessionOptions(
        namespace,
        idleTtl,
        lease,
        terminalRetention,
        maxBytes,
        maxReservations,
        maxTerminals,
        maxAttempts);
  }

  /**
   * Returns a safe configuration description without credentials.
   *
   * @return redacted transport description
   */
  @Override
  public String toString() {
    return "InertiaRedisProperties[endpoint=<configured>, credentials=<redacted>]";
  }
}
