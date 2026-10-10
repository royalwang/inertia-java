package io.inertia.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.inertia.core.*;
import io.inertia.spring.HttpSessionStore;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.mock.web.MockHttpSession;

/**
 * Explicit real-backend suite: -Dtest=RedisSessionStoreIT
 * -Dinertia.redis.server=/path/to/redis-server.
 */
class RedisSessionStoreIT {
  static Process server;
  static int port;
  static RedisSessionBackend backend;
  static final ObjectMapper json = new ObjectMapper();
  static Path work;
  static RedisSessionOptions defaults = RedisSessionOptions.defaults("qualification");

  @BeforeAll
  static void startRedis() throws Exception {
    String executable = System.getProperty("inertia.redis.server");
    assertNotNull(
        executable, "Explicit real redis-server executable required; no mock/skip fallback");
    assertTrue(Files.isExecutable(Path.of(executable)));
    try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    work = Files.createTempDirectory("inertia-real-redis-");
    server =
        new ProcessBuilder(
                executable,
                "--bind",
                "127.0.0.1",
                "--port",
                Integer.toString(port),
                "--protected-mode",
                "yes",
                "--save",
                "",
                "--appendonly",
                "no",
                "--maxmemory",
                "32mb",
                "--maxmemory-policy",
                "noeviction")
            .directory(work.toFile())
            .redirectErrorStream(true)
            .redirectOutput(work.resolve("redis.log").toFile())
            .start();
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (true) {
      assertTrue(server.isAlive(), "Owned Redis exited: " + work);
      try (var socket = new Socket("127.0.0.1", port)) {
        break;
      } catch (java.io.IOException notReady) {
        if (System.nanoTime() > deadline) throw notReady;
        Thread.sleep(25);
      }
    }
    backend = connect();
  }

  static RedisSessionBackend connect() {
    return new RedisSessionBackend(
        new RedisStandaloneConfiguration("127.0.0.1", port), Duration.ofMillis(500), 32);
  }

  @AfterAll
  static void stopRedis() throws Exception {
    if (backend != null) backend.close();
    if (server != null) {
      server.destroy();
      assertTrue(server.waitFor(5, TimeUnit.SECONDS), "Owned Redis failed graceful cleanup");
    }
  }

  static RedisSessionStore store() {
    return store(UUID.randomUUID().toString(), defaults);
  }

  static RedisSessionStore store(String identity, RedisSessionOptions options) {
    return new RedisSessionStore(backend, options, identity, null);
  }

  static ObjectNode object(String body) throws Exception {
    return (ObjectNode) json.readTree(body);
  }

  static void reason(
      RedisSessionException.Reason expected, org.junit.jupiter.api.function.Executable action) {
    assertEquals(expected, assertThrows(RedisSessionException.class, action).reason());
  }

  static Stream<SessionStore> commonStores() {
    return Stream.of(
        new MemorySessionStore(), new HttpSessionStore(new MockHttpSession()), store());
  }

  @ParameterizedTest
  @MethodSource("commonStores")
  void commonAtomicDeliveryAndCanonicalMerge(SessionStore store) throws Exception {
    var initial =
        object(
            "{\"flash\":{\"same\":\"old\",\"old\":true},\"boolean\":true,\"array\":[],\"large\":9223372036854775808123}");
    store.merge(initial);
    var token = store.beginPageDelivery();
    assertNull(store.get("flash"));
    token.data().removeAll(); // returned snapshots cannot mutate storage
    store.merge(object("{\"flash\":{\"same\":\"new\"},\"boolean\":false}"));
    store.abortPageDelivery(new SessionStore.Delivery(token.token(), json.createObjectNode()));
    var result = store.beginPageDelivery();
    assertEquals("new", result.data().path("flash").path("same").asText());
    assertTrue(result.data().path("flash").path("old").asBoolean());
    assertTrue(result.data().path("boolean").asBoolean());
    assertEquals(initial.get("array"), result.data().get("array"));
    assertEquals(initial.get("large"), result.data().get("large"));
    store.put("late", TextNode.valueOf("next"));
    store.completePageDelivery(result);
    assertEquals("next", store.get("late").asText());
    assertThrows(IllegalStateException.class, () -> store.abortPageDelivery(result));
  }

  @ParameterizedTest
  @MethodSource("commonStores")
  void commonErrorBagsAndFailureAtomicity(SessionStore store) throws Exception {
    var old = object("{\"inertia.errors\":{\"form-a\":{\"email\":[\"old\"]}}}");
    var next =
        object(
            "{\"inertia.errors\":{\"form-a\":{\"email\":[\"new\"]},\"form-b\":{\"name\":[\"required\"]}}}");
    store.merge(old);
    var reserved = store.beginPageDelivery();
    store.merge(next);
    store.abortPageDelivery(reserved);
    assertEquals(
        old.path(InertiaContext.ERRORS).path("form-a").path("email").get(0),
        store.get(InertiaContext.ERRORS).path("form-a").path("email").get(0));
    assertEquals(2, store.get(InertiaContext.ERRORS).path("form-a").path("email").size());
    var before = store.get(InertiaContext.ERRORS);
    assertThrows(
        IllegalArgumentException.class,
        () -> store.merge(object("{\"inertia.errors\":\"invalid\"}")));
    assertEquals(before, store.get(InertiaContext.ERRORS));
  }

  @Test
  void twoConnectionsReserveEachSnapshotOnlyOnceAndRaceTerminalOperations() throws Exception {
    String id = UUID.randomUUID().toString();
    var one = store(id, defaults);
    try (var independent = connect();
        var workers = Executors.newFixedThreadPool(2)) {
      var two = new RedisSessionStore(independent, defaults, id, one.epoch());
      one.put("flash", TextNode.valueOf("one"));
      var gate = new CountDownLatch(1);
      var a =
          workers.submit(
              () -> {
                gate.await();
                return one.beginPageDelivery();
              });
      var b =
          workers.submit(
              () -> {
                gate.await();
                return two.beginPageDelivery();
              });
      gate.countDown();
      var da = a.get(5, TimeUnit.SECONDS);
      var db = b.get(5, TimeUnit.SECONDS);
      assertEquals(1, (da.data().has("flash") ? 1 : 0) + (db.data().has("flash") ? 1 : 0));
      var chosen = da.data().has("flash") ? da : db;
      var race = new CountDownLatch(1);
      var complete =
          workers.submit(
              () -> {
                race.await();
                try {
                  one.completePageDelivery(chosen);
                  return true;
                } catch (RedisSessionException e) {
                  assertEquals(RedisSessionException.Reason.INVALID_TOKEN, e.reason());
                  return false;
                }
              });
      var abort =
          workers.submit(
              () -> {
                race.await();
                try {
                  two.abortPageDelivery(chosen);
                  return true;
                } catch (RedisSessionException e) {
                  assertEquals(RedisSessionException.Reason.INVALID_TOKEN, e.reason());
                  return false;
                }
              });
      race.countDown();
      assertNotEquals(complete.get(5, TimeUnit.SECONDS), abort.get(5, TimeUnit.SECONDS));
      reason(RedisSessionException.Reason.INVALID_TOKEN, () -> one.completePageDelivery(chosen));
      reason(
          RedisSessionException.Reason.INVALID_TOKEN,
          () ->
              two.abortPageDelivery(
                  new SessionStore.Delivery(UUID.randomUUID(), json.createObjectNode())));
    }
  }

  @Test
  void expiresLeasesRestoresOldToNewPriorityAndFencesLateTokens() throws Exception {
    var options =
        new RedisSessionOptions(
            "lease",
            Duration.ofSeconds(5),
            Duration.ofMillis(150),
            Duration.ofSeconds(1),
            65536,
            4,
            16,
            16);
    var store = store(UUID.randomUUID().toString(), options);
    store.merge(object("{\"flash\":{\"value\":\"old\",\"old\":true}}"));
    var first = store.beginPageDelivery();
    store.merge(object("{\"flash\":{\"value\":\"middle\",\"middle\":true}}"));
    var second = store.beginPageDelivery();
    store.merge(object("{\"flash\":{\"value\":\"new\"}}"));
    Thread.sleep(180);
    store.recoverExpired();
    var flash = store.get("flash");
    assertEquals("new", flash.path("value").asText());
    assertTrue(flash.path("old").asBoolean());
    assertTrue(flash.path("middle").asBoolean());
    reason(RedisSessionException.Reason.INVALID_TOKEN, () -> store.completePageDelivery(first));
    reason(RedisSessionException.Reason.INVALID_TOKEN, () -> store.abortPageDelivery(second));
  }

  @Test
  void revokedExpiredAndForeignDomainsFailClosed() throws Exception {
    String id = UUID.randomUUID().toString();
    var a = store(id, defaults);
    var token = a.beginPageDelivery();
    var b = new RedisSessionStore(backend, defaults, id, a.epoch());
    var foreign = store();
    reason(RedisSessionException.Reason.INVALID_TOKEN, () -> foreign.completePageDelivery(token));
    a.invalidate();
    reason(RedisSessionException.Reason.STALE_DOMAIN, () -> b.completePageDelivery(token));
    reason(RedisSessionException.Reason.STALE_DOMAIN, () -> store(id, defaults));
    var shortTtl =
        new RedisSessionOptions(
            "ttl",
            Duration.ofMillis(150),
            Duration.ofMillis(50),
            Duration.ofMillis(80),
            4096,
            2,
            4,
            16);
    var expiring = store(id, shortTtl);
    var oldEpoch = expiring.epoch();
    Thread.sleep(200);
    reason(
        RedisSessionException.Reason.STALE_DOMAIN,
        () -> expiring.put("late", TextNode.valueOf("forbidden")));
    reason(
        RedisSessionException.Reason.STALE_DOMAIN,
        () -> new RedisSessionStore(backend, shortTtl, id, oldEpoch));
    assertNotEquals(oldEpoch, store(id + "-rotated", shortTtl).epoch());
  }

  @Test
  void namespacesAndByteReservationTerminalLimitsAreAtomic() throws Exception {
    String id = UUID.randomUUID().toString();
    var small =
        new RedisSessionOptions(
            "small",
            Duration.ofSeconds(5),
            Duration.ofSeconds(1),
            Duration.ofMillis(100),
            1024,
            1,
            1,
            16);
    var a = store(id, small);
    var b = store(id, RedisSessionOptions.defaults("other"));
    a.put("data", TextNode.valueOf("retained"));
    assertNull(b.get("data"));
    reason(
        RedisSessionException.Reason.CAPACITY,
        () -> a.put("data", TextNode.valueOf("x".repeat(2048))));
    assertEquals("retained", a.get("data").asText());
    var first = a.beginPageDelivery();
    reason(RedisSessionException.Reason.CAPACITY, a::beginPageDelivery);
    a.completePageDelivery(first);
    var second = a.beginPageDelivery();
    reason(RedisSessionException.Reason.CAPACITY, () -> a.completePageDelivery(second));
    Thread.sleep(120);
    a.completePageDelivery(second);
  }

  @Test
  void decimalPrecisionAndFiniteJsonBounds() {
    var store = store();
    var decimal = new java.math.BigDecimal("0.12345678901234567890123456789");
    store.put("precise", DecimalNode.valueOf(decimal));
    assertEquals(decimal, store.get("precise").decimalValue());
    assertThrows(
        IllegalArgumentException.class, () -> store.put("precise", DoubleNode.valueOf(Double.NaN)));
    assertEquals(decimal, store.get("precise").decimalValue());
    var deep = json.createObjectNode();
    var child = deep;
    for (int depth = 0; depth < 50; depth++) child = child.putObject("nested");
    reason(RedisSessionException.Reason.CAPACITY, () -> store.put("deep", deep));
    assertNull(store.get("deep"));
    reason(
        RedisSessionException.Reason.CAPACITY,
        () -> store.put("huge-number", BigIntegerNode.valueOf(java.math.BigInteger.TEN.pow(1000))));
    assertNull(store.get("huge-number"));
  }

  @Test
  void opaqueCasRejectsStaleLeaseDeadlineAndClockReversalWithoutWriting() {
    String key = "inertia-it-direct-" + UUID.randomUUID();
    var read = backend.read(key);
    assertEquals(-3, backend.cas(key, read, "not-written", read.now(), 1000));
    assertEquals("", backend.read(key).json());
    var future = new RedisSessionBackend.Read("", read.now() + 10000);
    assertEquals(-2, backend.cas(key, future, "not-written", future.now() + 1000, 1000));
    assertEquals("", backend.read(key).json());
  }
}
