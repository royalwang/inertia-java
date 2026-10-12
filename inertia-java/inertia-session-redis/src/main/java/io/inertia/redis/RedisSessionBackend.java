package io.inertia.redis;

import static io.inertia.core.InertiaObserver.*;

import io.inertia.core.InertiaObserver;
import io.inertia.core.Observations;
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
  private final InertiaObserver observer;
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
    this(endpoint, timeout, maxCommands, RedisTlsOptions.disabled());
  }

  /**
   * Creates a bounded transport with explicit TLS trust and mandatory peer verification.
   *
   * @param endpoint standalone endpoint/authentication/database owned by the application
   * @param timeout positive connect, handshake and command timeout, at most five seconds
   * @param maxCommands positive concurrent command limit, at most 1024
   * @param tls immutable trust selection; never supplies reconnect/replay configuration
   * @throws IllegalArgumentException if transport bounds are invalid
   * @throws NullPointerException if endpoint or tls is null
   */
  public RedisSessionBackend(
      RedisStandaloneConfiguration endpoint,
      Duration timeout,
      int maxCommands,
      RedisTlsOptions tls) {
    this(endpoint, timeout, maxCommands, tls, InertiaObserver.NOOP);
  }

  /**
   * Creates an observed transport without exposing Redis keys, identities or values.
   *
   * @param endpoint application-owned standalone endpoint and authentication
   * @param timeout positive connect, handshake and command timeout, at most five seconds
   * @param maxCommands positive concurrent command limit, at most 1024
   * @param tls immutable TLS trust selection
   * @param observer synchronous bounded event sink; RuntimeException is isolated
   * @throws IllegalArgumentException if transport bounds are invalid
   * @throws NullPointerException if endpoint, tls or observer is null
   */
  public RedisSessionBackend(
      RedisStandaloneConfiguration endpoint,
      Duration timeout,
      int maxCommands,
      RedisTlsOptions tls,
      InertiaObserver observer) {
    this.observer = java.util.Objects.requireNonNull(observer);
    java.util.Objects.requireNonNull(tls);
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
            .socketOptions(io.lettuce.core.SocketOptions.builder().connectTimeout(timeout).build());
    if (tls.enabled()) options.sslOptions(tls.sslOptions(timeout));
    var configuration =
        LettuceClientConfiguration.builder().commandTimeout(timeout).clientOptions(options.build());
    if (tls.enabled()) configuration.useSsl();
    factory =
        new LettuceConnectionFactory(
            java.util.Objects.requireNonNull(endpoint), configuration.build());
    factory.setShareNativeConnection(false);
    factory.afterPropertiesSet();
    factory.start();
    redis = new StringRedisTemplate(factory);
  }

  record Read(String json, long now) {}

  Read read(String key) {
    long started = System.nanoTime();
    if (!permits.tryAcquire()) {
      observe(Operation.REDIS_READ, Outcome.FAILURE, Reason.OVERLOADED, started);
      throw new RedisSessionException(RedisSessionException.Reason.CAPACITY);
    }
    var outcome = Outcome.FAILURE;
    var reason = Reason.TRANSPORT;
    try {
      var result = redis.execute(READ, List.of(key));
      if (result == null || result.size() != 2) throw new IllegalStateException();
      var read = new Read((String) result.get(0), Long.parseLong((String) result.get(1)));
      outcome = Outcome.SUCCESS;
      reason = Reason.NONE;
      return read;
    } catch (RuntimeException failure) {
      throw new RedisSessionException(RedisSessionException.Reason.READ_FAILED);
    } finally {
      permits.release();
      observe(Operation.REDIS_READ, outcome, reason, started);
    }
  }

  long cas(String key, Read before, String after, long deadline, long ttl) {
    long started = System.nanoTime();
    if (!permits.tryAcquire()) {
      observe(Operation.REDIS_CAS, Outcome.FAILURE, Reason.OVERLOADED, started);
      throw new RedisSessionException(RedisSessionException.Reason.CAPACITY);
    }
    var outcome = Outcome.FAILURE;
    var reason = Reason.REDIS_UNKNOWN_WRITE;
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
      outcome = result == 1 ? Outcome.SUCCESS : result == -2 ? Outcome.FAILURE : Outcome.CONFLICT;
      reason = result == -3 ? Reason.TIMEOUT : result == -2 ? Reason.ERROR : Reason.NONE;
      return result;
    } catch (RuntimeException failure) {
      throw new RedisSessionException(RedisSessionException.Reason.UNKNOWN_WRITE);
    } finally {
      permits.release();
      observe(Operation.REDIS_CAS, outcome, reason, started);
    }
  }

  private void observe(Operation operation, Outcome outcome, Reason reason, long started) {
    if (observer != InertiaObserver.NOOP)
      Observations.publish(
          observer,
          new Event(
              operation,
              outcome,
              reason,
              System.nanoTime() - started,
              0,
              ResponseKind.NONE,
              "",
              "",
              "redis"));
  }

  void stateFailure(RedisSessionException.Reason failure) {
    if (observer == InertiaObserver.NOOP) return;
    var reason =
        switch (failure) {
          case READ_FAILED -> Reason.TRANSPORT;
          case UNKNOWN_WRITE -> Reason.REDIS_UNKNOWN_WRITE;
          case CAPACITY -> Reason.REDIS_CAPACITY;
          case CONTENTION -> Reason.REDIS_CONTENTION;
          case BUDGET_EXHAUSTED -> Reason.REDIS_BUDGET_EXHAUSTED;
          case STALE_DOMAIN -> Reason.REDIS_STALE_DOMAIN;
          case INVALID_TOKEN -> Reason.REDIS_INVALID_TOKEN;
          case INVALID_STATE -> Reason.REDIS_INVALID_STATE;
          case CLOCK_REVERSED -> Reason.REDIS_CLOCK_REVERSED;
        };
    Observations.publish(
        observer,
        new Event(
            Operation.REDIS_STATE_FAILURE,
            Outcome.FAILURE,
            reason,
            0,
            0,
            ResponseKind.NONE,
            "",
            "",
            "redis"));
  }

  /** Closes the owned Redis connections and Lettuce resources. */
  @Override
  public void close() {
    factory.destroy();
  }
}
