package io.inertia.redis;

import io.lettuce.core.ClientOptions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Application-owned standalone Redis transport with at-most-once command submission. Uses Spring
 * Data Redis/Lettuce, disables automatic reconnect/replay and disconnected queuing, and closes each
 * dedicated command connection. A later operation may open a new connection; an unknown mutation is
 * never repeated. Not a Cluster/Sentinel or failover qualification. Close this backend once when
 * the application shuts down, never after each request.
 */
public final class RedisSessionBackend implements AutoCloseable {
  private final LettuceConnectionFactory factory;
  private final StringRedisTemplate redis;
  private final Semaphore permits;
  private static final DefaultRedisScript<List> READ =
      new DefaultRedisScript<>(
          """
      local t = redis.call('TIME')
      local now = t[1] * 1000 + math.floor(t[2] / 1000)
      return {redis.call('GET', KEYS[1]) or '', tostring(now)}
      """,
          List.class);
  private static final DefaultRedisScript<Long> CAS =
      new DefaultRedisScript<>(
          """
      local t = redis.call('TIME')
      local now = t[1] * 1000 + math.floor(t[2] / 1000)
      if now < tonumber(ARGV[3]) then return -2 end
      if now >= tonumber(ARGV[4]) then return -3 end
      if (redis.call('GET', KEYS[1]) or '') ~= ARGV[1] then return 0 end
      redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[5])
      return 1
      """,
          Long.class);

  /**
   * Creates a dedicated transport with bounded concurrent commands and no mutation replay.
   *
   * @param endpoint standalone Redis endpoint/authentication/database, owned by the application
   * @param timeout positive command/connect timeout, at most five seconds
   * @param maxCommands positive concurrent command limit
   * @throws IllegalArgumentException if bounds are invalid
   */
  public RedisSessionBackend(
      RedisStandaloneConfiguration endpoint, Duration timeout, int maxCommands) {
    if (timeout == null
        || timeout.toMillis() < 1
        || timeout.compareTo(Duration.ofSeconds(5)) > 0
        || maxCommands < 1
        || maxCommands > 1024) throw new IllegalArgumentException("Invalid Redis transport bounds");
    permits = new Semaphore(maxCommands);
    var options =
        ClientOptions.builder()
            .autoReconnect(false)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .requestQueueSize(1)
            .socketOptions(io.lettuce.core.SocketOptions.builder().connectTimeout(timeout).build())
            .build();
    factory =
        new LettuceConnectionFactory(
            java.util.Objects.requireNonNull(endpoint),
            LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .clientOptions(options)
                .build());
    factory.setShareNativeConnection(false);
    factory.afterPropertiesSet();
    factory.start();
    redis = new StringRedisTemplate(factory);
  }

  record Read(String json, long now) {}

  Read read(String key) {
    if (!permits.tryAcquire())
      throw new RedisSessionException(RedisSessionException.Reason.CAPACITY);
    try {
      var result = redis.execute(READ, List.of(key));
      if (result == null || result.size() != 2) throw new IllegalStateException();
      return new Read((String) result.get(0), Long.parseLong((String) result.get(1)));
    } catch (RuntimeException failure) {
      throw new RedisSessionException(RedisSessionException.Reason.READ_FAILED);
    } finally {
      permits.release();
    }
  }

  long cas(String key, Read before, String after, long deadline, long ttl) {
    if (!permits.tryAcquire())
      throw new RedisSessionException(RedisSessionException.Reason.CAPACITY);
    try {
      var result =
          redis.execute(
              CAS,
              List.of(key),
              before.json(),
              after,
              Long.toString(before.now()),
              Long.toString(deadline),
              Long.toString(ttl));
      if (result == null) throw new IllegalStateException();
      return result;
    } catch (RuntimeException failure) {
      throw new RedisSessionException(RedisSessionException.Reason.UNKNOWN_WRITE);
    } finally {
      permits.release();
    }
  }

  /** Closes the owned Redis connections and Lettuce resources. */
  @Override
  public void close() {
    factory.destroy();
  }
}
