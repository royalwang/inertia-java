package io.inertia.ssr;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.inertia.core.PageCodec;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;

class SsrHealthMonitorTest {
  HttpServer server;
  ExecutorService workers;
  AtomicInteger status = new AtomicInteger(200);
  AtomicReference<String> body = new AtomicReference<>("{\"status\":\"OK\"}");
  AtomicInteger calls = new AtomicInteger();
  AtomicInteger redirected = new AtomicInteger();
  AtomicInteger active = new AtomicInteger();
  AtomicInteger maxActive = new AtomicInteger();
  AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();
  volatile CountDownLatch hold;
  CountDownLatch entered = new CountDownLatch(1);
  CountDownLatch finished = new CountDownLatch(1);

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    workers = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(workers);
    server.createContext(
        "/health",
        exchange -> {
          calls.incrementAndGet();
          maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
          headers.set(exchange.getRequestHeaders());
          entered.countDown();
          try {
            var gate = hold;
            if (gate != null) gate.await(2, TimeUnit.SECONDS);
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "/target");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } catch (java.io.IOException disconnected) {
          } finally {
            exchange.close();
            active.decrementAndGet();
            finished.countDown();
          }
        });
    server.createContext(
        "/target",
        exchange -> {
          redirected.incrementAndGet();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    if (hold != null) hold.countDown();
    server.stop(0);
    workers.shutdownNow();
  }

  SsrHealthMonitor monitor(Duration timeout, Duration interval) {
    return new SsrHealthMonitor(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health"),
        Duration.ofSeconds(1),
        timeout,
        interval,
        new PageCodec());
  }

  void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
    assertTrue(condition.getAsBoolean());
  }

  @Test
  void unknownBeforeStartAndSnapshotNeverSendsRequests() throws Exception {
    try (var monitor = monitor(Duration.ofSeconds(1), Duration.ofSeconds(10))) {
      assertEquals(SsrHealthMonitor.State.UNKNOWN, monitor.snapshot().state());
      assertNull(monitor.snapshot().checkedAt());
      assertEquals(0, calls.get());
      monitor.start().start();
      await(() -> monitor.snapshot().state() == SsrHealthMonitor.State.UP);
      for (int i = 0; i < 100; i++) assertEquals("healthy", monitor.snapshot().reason());
      assertEquals(1, calls.get());
      assertNull(headers.get().getFirst("Cookie"));
      assertNull(headers.get().getFirst("Authorization"));
    }
  }

  @Test
  void badStatusSchemaAndOversizeAreDownAndCanRecover() throws Exception {
    try (var monitor = monitor(Duration.ofSeconds(1), Duration.ofMillis(30)).start()) {
      await(() -> monitor.snapshot().state() == SsrHealthMonitor.State.UP);
      status.set(302);
      await(() -> monitor.snapshot().reason().equals("http-status"));
      assertEquals(0, redirected.get());
      status.set(200);
      body.set("{\"status\":\"BAD\",\"private\":\"must not be exposed\"}");
      await(() -> monitor.snapshot().reason().equals("invalid-response"));
      body.set("x".repeat(8192));
      await(() -> monitor.snapshot().reason().equals("transport-or-timeout"));
      body.set("{\"status\":\"OK\"}");
      await(() -> monitor.snapshot().state() == SsrHealthMonitor.State.UP);
      assertNotNull(monitor.snapshot().checkedAt());
    }
  }

  @Test
  void backgroundChecksDoNotOverlapAndCloseCannotPublishLateUp() throws Exception {
    hold = new CountDownLatch(1);
    var monitor = monitor(Duration.ofSeconds(1), Duration.ofMillis(10)).start();
    try {
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      for (int i = 0; i < 100; i++) monitor.snapshot();
      assertEquals(1, calls.get());
      assertEquals(1, maxActive.get());
      monitor.close();
      hold.countDown();
      assertTrue(finished.await(2, TimeUnit.SECONDS));
      assertEquals(SsrHealthMonitor.State.STOPPED, monitor.snapshot().state());
      assertThrows(IllegalStateException.class, monitor::start);
    } finally {
      monitor.close();
    }
  }

  @Test
  void slowProbeHasDeadlineAndInvalidConfigurationFails() throws Exception {
    hold = new CountDownLatch(1);
    try (var monitor = monitor(Duration.ofMillis(150), Duration.ofSeconds(10)).start()) {
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      await(() -> monitor.snapshot().state() == SsrHealthMonitor.State.DOWN);
      assertEquals("transport-or-timeout", monitor.snapshot().reason());
    }
    assertThrows(
        IllegalArgumentException.class, () -> monitor(Duration.ZERO, Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class, () -> monitor(Duration.ofSeconds(1), Duration.ZERO));
  }
}
