package io.inertia.ssr;

import io.inertia.core.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;

/** Independent background health sampling. Reading a snapshot never sends an HTTP request. */
public final class SsrHealthMonitor implements AutoCloseable {
  public enum State {
    UNKNOWN,
    UP,
    DOWN,
    STOPPED
  }

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

  public synchronized SsrHealthMonitor start() {
    if (closed) throw new IllegalStateException("Health monitor is closed");
    if (!started) {
      started = true;
      scheduler.scheduleWithFixedDelay(this::check, 0, intervalNanos, TimeUnit.NANOSECONDS);
    }
    return this;
  }

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
