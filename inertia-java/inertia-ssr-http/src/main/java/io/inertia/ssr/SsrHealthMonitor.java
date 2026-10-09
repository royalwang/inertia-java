package io.inertia.ssr;

import io.inertia.core.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;

/**
 * Application-owned background health sampler, independent from Page rendering.
 *
 * <p>Reading a snapshot never sends a request. Sampling requires HTTP 200 and a JSON {@code status:
 * OK} within 4096 response bytes. Health does not verify build/root identity, component rendering
 * or hydration. Close the monitor when its application owner stops.
 */
public final class SsrHealthMonitor implements AutoCloseable {
  /** Lifecycle/result category of the cached health snapshot. */
  public enum State {
    /** No completed check has published a result. */
    UNKNOWN,
    /** The latest check received the expected health response. */
    UP,
    /** The latest check failed its transport/status/shape contract. */
    DOWN,
    /** The owner closed this monitor; late checks cannot revive it. */
    STOPPED
  }

  /**
   * Immutable cached observation, not a synchronous readiness decision.
   *
   * @param state lifecycle or last result category
   * @param reason bounded internal classification; no renderer body or exception message
   * @param checkedAt time of the last published check, or null before the first result
   */
  public record Snapshot(State state, String reason, Instant checkedAt) {}

  private final URI endpoint;
  private final Duration timeout;
  private final long timeoutNanos;
  private final long intervalNanos;
  private final HttpClient client;
  private final PageCodec codec;
  private final ScheduledExecutorService scheduler;
  private volatile Snapshot snapshot = new Snapshot(State.UNKNOWN, "not-checked", null);
  private volatile CompletableFuture<HttpResponse<byte[]>> pending;
  private volatile boolean closed;
  private boolean started;

  /**
   * Creates owned HTTP/scheduler resources without starting checks.
   *
   * @param endpoint trusted absolute HTTP(S) health URL
   * @param connectTimeout positive connection-establishment budget
   * @param timeout positive total budget for each health request
   * @param interval positive fixed delay after one check finishes
   * @param codec shared codec for the health response
   * @throws IllegalArgumentException if endpoint policy or any budget is invalid
   */
  public SsrHealthMonitor(
      URI endpoint, Duration connectTimeout, Duration timeout, Duration interval, PageCodec codec) {
    this.endpoint = ConfiguredHttpUrl.endpoint(endpoint);
    if (connectTimeout.isNegative()
        || connectTimeout.isZero()
        || timeout.isNegative()
        || timeout.isZero()
        || interval.isNegative()
        || interval.isZero()) throw new IllegalArgumentException("Invalid health budgets");
    this.timeout = timeout;
    timeoutNanos = timeout.toNanos();
    intervalNanos = interval.toNanos();
    this.codec = codec;
    client =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("inertia-ssr-health").factory());
  }

  /**
   * Starts fixed-delay checks immediately; repeated calls while open are idempotent.
   *
   * @return this owned monitor
   * @throws IllegalStateException if already closed
   */
  public synchronized SsrHealthMonitor start() {
    if (closed) throw new IllegalStateException("Health monitor is closed");
    if (!started) {
      started = true;
      scheduler.scheduleWithFixedDelay(this::check, 0, intervalNanos, TimeUnit.NANOSECONDS);
    }
    return this;
  }

  /**
   * Reads the latest volatile snapshot without waiting for or initiating transport.
   *
   * @return cached UNKNOWN/UP/DOWN/STOPPED state and its check timestamp
   */
  public Snapshot snapshot() {
    return snapshot;
  }

  private void check() {
    if (closed) return;
    CompletableFuture<HttpResponse<byte[]>> operation = null;
    try {
      var request = HttpRequest.newBuilder(endpoint).timeout(timeout).GET().build();
      operation = client.sendAsync(request, info -> new HttpSsrGateway.LimitedBody(4096));
      pending = operation;
      if (closed) {
        operation.cancel(true);
        return;
      }
      var response = operation.get(timeoutNanos, TimeUnit.NANOSECONDS);
      if (response.statusCode() != 200) {
        publish(State.DOWN, "http-status");
        return;
      }
      var json = codec.read(new String(response.body(), StandardCharsets.UTF_8));
      if (json == null || !json.isObject() || !"OK".equals(json.path("status").asText()))
        publish(State.DOWN, "invalid-response");
      else publish(State.UP, "healthy");
    } catch (InterruptedException interrupted) {
      if (operation != null) operation.cancel(true);
      Thread.currentThread().interrupt();
      publish(State.DOWN, "interrupted");
    } catch (TimeoutException | ExecutionException failure) {
      if (operation != null) operation.cancel(true);
      publish(State.DOWN, "transport-or-timeout");
    } catch (RuntimeException failure) {
      if (operation != null) operation.cancel(true);
      publish(State.DOWN, "invalid-response");
    } finally {
      pending = null;
    }
  }

  private synchronized void publish(State state, String reason) {
    if (!closed) snapshot = new Snapshot(state, reason, Instant.now());
  }

  /**
   * Permanently stops sampling and cancels the owned pending request and scheduler.
   *
   * <p>Repeated close is harmless. The STOPPED snapshot retains the previous checkedAt timestamp;
   * late asynchronous completion cannot publish a new UP/DOWN state.
   */
  @Override
  public void close() {
    synchronized (this) {
      if (closed) return;
      closed = true;
      snapshot = new Snapshot(State.STOPPED, "closed", snapshot.checkedAt());
    }
    var operation = pending;
    if (operation != null) operation.cancel(true);
    scheduler.shutdownNow();
    client.shutdownNow();
  }
}
