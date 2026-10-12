package io.inertia.redis;

import static io.inertia.core.InertiaObserver.*;
import static io.inertia.redis.RedisSessionStoreIT.*;
import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.InertiaObserver;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

/** Real command outcomes and bounded diagnostics; no mock backend. */
class RedisObservationIT {
  @Test
  void explicitConflictExhaustionIsObservedAndANewOperationRecovers() {
    var events = new CopyOnWriteArrayList<Event>();
    var options =
        new RedisSessionOptions(
            "observed-contention",
            Duration.ofSeconds(10),
            Duration.ofSeconds(2),
            Duration.ofSeconds(3),
            4096,
            4,
            16,
            1);
    var identity = java.util.UUID.randomUUID().toString();
    var other = new RedisSessionStore(backend, options, identity, null);
    var armed = new AtomicBoolean();
    try (var transport =
        observed(
            4,
            event -> {
              events.add(event);
              if (event.operation() == Operation.REDIS_READ && armed.compareAndSet(true, false))
                other.put("racer", new io.inertia.core.PageCodec().value("kept"));
            })) {
      var store = new RedisSessionStore(transport, options, identity, other.epoch());
      events.clear();
      armed.set(true);
      reason(
          RedisSessionException.Reason.CONTENTION,
          () -> store.put("loser", new io.inertia.core.PageCodec().value("not-written")));
      assertEquals(Reason.REDIS_CONTENTION, events.getLast().reason());
      assertTrue(
          events.stream()
              .anyMatch(
                  e -> e.operation() == Operation.REDIS_CAS && e.outcome() == Outcome.CONFLICT));
      assertNull(other.get("loser"));
      store.put("next", new io.inertia.core.PageCodec().value("recovered"));
      assertEquals("recovered", other.get("next").asText());
    }
  }

  @Test
  void realRedisPauseExhaustsDispatchBudgetBeforeCasAndThenRecovers() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    try (var transport =
        new RedisSessionBackend(
            new RedisStandaloneConfiguration("127.0.0.1", port),
            Duration.ofSeconds(3),
            4,
            RedisTlsOptions.disabled(),
            events::add)) {
      var store =
          new RedisSessionStore(
              transport,
              RedisSessionOptions.defaults("observed-budget"),
              java.util.UUID.randomUUID().toString(),
              null);
      store.put("kept", new io.inertia.core.PageCodec().value("before"));
      events.clear();
      try (var control = new Socket("127.0.0.1", port)) {
        control.setSoTimeout(2000);
        control
            .getOutputStream()
            .write(
                "*3\r\n$6\r\nCLIENT\r\n$5\r\nPAUSE\r\n$4\r\n1500\r\n"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        control.getOutputStream().flush();
        assertEquals(
            "+OK\r\n",
            new String(
                RedisTlsIT.frame(control.getInputStream()),
                java.nio.charset.StandardCharsets.US_ASCII));
        reason(
            RedisSessionException.Reason.BUDGET_EXHAUSTED,
            () -> store.put("kept", new io.inertia.core.PageCodec().value("not-written")));
      }
      assertEquals(Reason.REDIS_BUDGET_EXHAUSTED, events.getLast().reason());
      assertFalse(events.stream().anyMatch(e -> e.operation() == Operation.REDIS_CAS));
      assertEquals("before", store.get("kept").asText());
      store.put("kept", new io.inertia.core.PageCodec().value("recovered"));
      assertEquals("recovered", store.get("kept").asText());
    }
  }

  @Test
  void stateCapacityAndInvalidTokenPublishExactBoundedReasonsOnce() {
    var events = new CopyOnWriteArrayList<Event>();
    try (var transport = observed(4, events::add)) {
      var options =
          new RedisSessionOptions(
              "observed-bounds",
              Duration.ofSeconds(10),
              Duration.ofSeconds(1),
              Duration.ofSeconds(2),
              1024,
              1,
              1,
              16);
      var store = new RedisSessionStore(transport, options, "private-state-identity", null);
      store.put("value", new io.inertia.core.PageCodec().value("private-business-value"));
      var held = store.beginPageDelivery();
      reason(RedisSessionException.Reason.CAPACITY, store::beginPageDelivery);
      store.completePageDelivery(held);
      reason(RedisSessionException.Reason.INVALID_TOKEN, () -> store.abortPageDelivery(held));
      var failures =
          events.stream().filter(e -> e.operation() == Operation.REDIS_STATE_FAILURE).toList();
      assertEquals(2, failures.size());
      assertEquals(Reason.REDIS_CAPACITY, failures.getFirst().reason());
      assertEquals(Reason.REDIS_INVALID_TOKEN, failures.getLast().reason());
      assertTrue(
          failures.stream().allMatch(e -> e.elapsedNanos() == 0 && e.outcome() == Outcome.FAILURE));
      assertFalse(failures.toString().contains("private-"));
    }
  }

  @BeforeAll
  static void start() throws Exception {
    startRedis();
  }

  @AfterAll
  static void stop() throws Exception {
    stopRedis();
  }

  RedisSessionBackend observed(int commands, InertiaObserver observer) {
    return new RedisSessionBackend(
        new RedisStandaloneConfiguration("127.0.0.1", port),
        Duration.ofMillis(500),
        commands,
        RedisTlsOptions.disabled(),
        observer);
  }

  @Test
  void commandsClassifyConflictsAndDeadlinesWithoutIdentitiesOrValues() {
    var events = new CopyOnWriteArrayList<Event>();
    try (var transport = observed(2, events::add)) {
      var before = transport.read("private-identity");
      assertEquals(
          1,
          transport.cas(
              "private-identity", before, "private-business-value", before.now() + 1000, 10000));
      assertEquals(
          0, transport.cas("private-identity", before, "unwritten", before.now() + 1000, 10000));
      var fresh = transport.read("private-identity");
      assertEquals(-3, transport.cas("private-identity", fresh, "unwritten", fresh.now(), 10000));
      var future = new RedisSessionBackend.Read(fresh.json(), fresh.now() + 10000);
      assertEquals(
          -2, transport.cas("private-identity", future, "unwritten", future.now() + 10000, 10000));
      assertEquals(6, events.size());
      assertEquals(
          2,
          events.stream()
              .filter(e -> e.operation() == Operation.REDIS_READ && e.outcome() == Outcome.SUCCESS)
              .count());
      assertEquals(
          1,
          events.stream()
              .filter(
                  e ->
                      e.operation() == Operation.REDIS_CAS
                          && e.outcome() == Outcome.CONFLICT
                          && e.reason() == Reason.NONE)
              .count());
      assertEquals(1, events.stream().filter(e -> e.reason() == Reason.TIMEOUT).count());
      assertEquals(1, events.stream().filter(e -> e.reason() == Reason.ERROR).count());
      for (var event : events) {
        assertEquals("", event.requestId());
        assertEquals("", event.component());
        assertEquals("redis", event.endpointId());
        assertEquals(0, event.status());
        assertEquals(ResponseKind.NONE, event.response());
        assertTrue(event.elapsedNanos() >= 0);
        assertFalse(event.toString().contains("private-"));
      }
    }
  }

  @Test
  void actualBlockedConnectionExhaustsPermitsAndThenReleasesThem() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    try (var listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        var transport =
            new RedisSessionBackend(
                new RedisStandaloneConfiguration("127.0.0.1", listener.getLocalPort()),
                Duration.ofMillis(500),
                1,
                RedisTlsOptions.disabled(),
                events::add)) {
      var pending =
          workers.submit(
              () -> assertThrows(RedisSessionException.class, () -> transport.read("not-logged")));
      listener.setSoTimeout(2000);
      try (var peer = listener.accept()) {
        var rejection =
            assertThrows(RedisSessionException.class, () -> transport.read("not-submitted"));
        assertEquals(RedisSessionException.Reason.CAPACITY, rejection.reason());
        assertEquals(Reason.OVERLOADED, events.getFirst().reason());
        assertEquals(
            RedisSessionException.Reason.READ_FAILED, pending.get(3, TimeUnit.SECONDS).reason());
      }
      assertEquals(2, events.size());
      assertEquals(Reason.TRANSPORT, events.getLast().reason());
      // A subsequent explicit call can acquire the released permit, even though the peer still
      // fails.
      assertEquals(
          RedisSessionException.Reason.READ_FAILED,
          assertThrows(RedisSessionException.class, () -> transport.read("new-operation"))
              .reason());
    }
  }

  @Test
  void observerExceptionsCannotReplaceSuccessfulCommands() {
    try (var transport =
        observed(
            1,
            event -> {
              throw new IllegalStateException("observer failure");
            })) {
      var before = transport.read("observer-isolated");
      assertEquals(
          1, transport.cas("observer-isolated", before, "committed", before.now() + 1000, 10000));
      assertEquals("committed", transport.read("observer-isolated").json());
    }
  }

  @Test
  void permitIsReleasedBeforePublishingTheEvent() {
    var reference = new AtomicReference<RedisSessionBackend>();
    var entered = new AtomicBoolean();
    var nested = new AtomicInteger();
    try (var transport =
        observed(
            1,
            event -> {
              if (entered.compareAndSet(false, true)) {
                reference.get().read("observer-nested-read");
                nested.incrementAndGet();
              }
            })) {
      reference.set(transport);
      transport.read("outer-read");
      assertEquals(1, nested.get());
    }
  }
}
