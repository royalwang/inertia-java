package io.inertia.boot;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.inertia.core.*;
import io.inertia.redis.*;
import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Owned real Redis + embedded Tomcat, actual listener/filter registration and HTTP cookies. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisHostLifecycleIT {
  static String executable;
  static int redisPort;
  static Process redis;
  static Path work;
  static ConfigurableApplicationContext application;
  static String base;
  static final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void start() throws Exception {
    executable = System.getProperty("inertia.redis.server");
    assertNotNull(executable, "Real Redis executable required; no mock/skip fallback");
    work = Files.createTempDirectory("inertia-redis-host-");
    try (var socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      redisPort = socket.getLocalPort();
    }
    startRedis();
    application =
        new org.springframework.boot.builder.SpringApplicationBuilder(HostApplication.class)
            .properties(
                "server.address=127.0.0.1",
                "server.port=0",
                "spring.main.banner-mode=off",
                "inertia.session.store=redis",
                "inertia.session-namespace=host-proof",
                "inertia.session.redis.host=127.0.0.1",
                "inertia.session.redis.port=" + redisPort,
                "inertia.session.redis.command-timeout=100ms",
                "inertia.session.redis.idle-ttl=20s",
                "inertia.session.redis.lease=2s",
                "inertia.session.redis.terminal-retention=1s",
                "inertia.response-timeout=1s",
                "inertia.props-timeout=500ms")
            .run();
    base =
        "http://127.0.0.1:"
            + ((ServletWebServerApplicationContext) application).getWebServer().getPort();
  }

  static void startRedis() throws Exception {
    redis =
        new ProcessBuilder(
                executable,
                "--bind",
                "127.0.0.1",
                "--port",
                Integer.toString(redisPort),
                "--protected-mode",
                "yes",
                "--save",
                "",
                "--appendonly",
                "no")
            .directory(work.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(work.resolve("redis.log").toFile()))
            .start();
    long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (true) {
      assertTrue(redis.isAlive());
      try (var socket = new Socket("127.0.0.1", redisPort)) {
        return;
      } catch (java.io.IOException notReady) {
        if (System.nanoTime() > until) throw notReady;
        Thread.sleep(20);
      }
    }
  }

  @AfterAll
  static void stop() throws Exception {
    if (application != null) application.close();
    if (redis != null) {
      redis.destroy();
      assertTrue(redis.waitFor(5, TimeUnit.SECONDS));
    }
  }

  static HttpClient client() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(2))
        .build();
  }

  static HttpResponse<String> get(HttpClient client, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(5))
            .header("X-Inertia", "true")
            .header("X-Inertia-Version", "v1")
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  static Held held() {
    return application.getBean(HostController.class).held;
  }

  static void assertRedisRevoked(Held held) {
    var backend = application.getBean(RedisSessionBackend.class);
    var failure =
        assertThrows(
            RedisSessionException.class,
            () ->
                new RedisSessionStore(
                    backend,
                    RedisSessionOptions.defaults("host-proof"),
                    held.identity,
                    held.epoch));
    assertEquals(RedisSessionException.Reason.STALE_DOMAIN, failure.reason());
  }

  @Test
  @Order(1)
  void nativeRotationAndDestructionRevokeInflightAndLaterVisitsRecover() throws Exception {
    var client = client();
    assertEquals(303, get(client, "/seed").statusCode());
    assertEquals(200, get(client, "/reserve").statusCode());
    var before = held();
    assertEquals(
        "saved", before.delivery.data().path(InertiaContext.FLASH).path("notice").asText());
    assertEquals(200, get(client, "/rotate").statusCode());
    assertThrows(
        IllegalStateException.class, () -> before.store.completePageDelivery(before.delivery));
    assertRedisRevoked(before);
    var page = get(client, "/page");
    assertEquals(200, page.statusCode());
    assertFalse(json.readTree(page.body()).has("flash"));
    assertEquals(303, get(client, "/seed").statusCode());
    assertEquals(200, get(client, "/reserve").statusCode());
    var destroyed = held();
    assertEquals(200, get(client, "/invalidate").statusCode());
    assertThrows(
        IllegalStateException.class, () -> destroyed.store.abortPageDelivery(destroyed.delivery));
    assertRedisRevoked(destroyed);
    assertEquals(200, get(client, "/page").statusCode());
  }

  @Test
  @Order(2)
  void copiedHostAttributesDoNotMoveOldDeliveryToNewIdentity() throws Exception {
    var client = client();
    get(client, "/seed");
    get(client, "/reserve");
    var before = held();
    assertEquals(200, get(client, "/migrate").statusCode());
    assertRedisRevoked(before);
    var page = get(client, "/page");
    assertEquals(200, page.statusCode());
    assertFalse(json.readTree(page.body()).has("flash"));
    assertThrows(
        IllegalStateException.class, () -> before.store.completePageDelivery(before.delivery));
  }

  @Test
  @Order(3)
  void failedRevocationPreventsHostRotationAndRetainsReconciliationMetadata() throws Exception {
    var client = client();
    get(client, "/seed");
    get(client, "/reserve");
    var before = held();
    redis.destroy();
    assertTrue(redis.waitFor(5, TimeUnit.SECONDS));
    try {
      assertEquals(500, get(client, "/rotate").statusCode());
      assertEquals(
          before.identity,
          before.session.getId(),
          "Host identity cannot change after failed revocation");
      assertTrue(
          ((String) before.session.getAttribute("io.inertia.redis.epoch.host-proof"))
              .startsWith("revoking."));
    } finally {
      startRedis();
    }
    assertThrows(
        IllegalStateException.class, () -> before.store.completePageDelivery(before.delivery));
    assertEquals(
        200,
        get(client, "/rotate").statusCode(),
        "Explicit later host operation reconciles known missing state");
    assertEquals(200, get(client, "/page").statusCode());
    assertRedisRevoked(before);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import(HostController.class)
  static class HostApplication {
    @Bean
    InertiaConfig inertiaConfig() {
      return InertiaConfig.basic("v1", Set.of("Home"));
    }
  }

  record Held(
      SessionStore store,
      SessionStore.Delivery delivery,
      HttpSession session,
      String identity,
      UUID epoch) {}

  @Controller
  static class HostController {
    final RedisHttpSessionStoreFactory factory;
    final RedisSessionBackend backend;
    volatile Held held;

    HostController(RedisHttpSessionStoreFactory factory, RedisSessionBackend backend) {
      this.factory = factory;
      this.backend = backend;
    }

    @GetMapping("/seed")
    public HttpOutcome seed(InertiaContext context) {
      context.flash("notice", "saved");
      return HttpOutcome.empty(303).withHeader("Location", "/page");
    }

    @GetMapping("/page")
    public InertiaResponse page() {
      return new InertiaResponse("Home", Props.empty());
    }

    @GetMapping("/reserve")
    @ResponseBody
    public Map<String, Boolean> reserve(HttpServletRequest request) {
      var store = factory.create(request);
      var session = request.getSession();
      var epoch =
          new RedisSessionStore(
                  backend, RedisSessionOptions.defaults("host-proof"), session.getId(), null)
              .epoch();
      held = new Held(store, store.beginPageDelivery(), session, session.getId(), epoch);
      return Map.of("reserved", true);
    }

    @GetMapping("/rotate")
    @ResponseBody
    public Map<String, Boolean> rotate(HttpServletRequest request) {
      request.changeSessionId();
      return Map.of("rotated", true);
    }

    @GetMapping("/invalidate")
    @ResponseBody
    public Map<String, Boolean> invalidate(HttpServletRequest request) {
      request.getSession().invalidate();
      return Map.of("invalidated", true);
    }

    @GetMapping("/migrate")
    @ResponseBody
    public Map<String, Boolean> migrate(HttpServletRequest request) {
      var session = request.getSession();
      var copied = new LinkedHashMap<String, Object>();
      Collections.list(session.getAttributeNames())
          .forEach(name -> copied.put(name, session.getAttribute(name)));
      session.invalidate();
      var replacement = request.getSession(true);
      copied.forEach(replacement::setAttribute);
      return Map.of("migrated", true);
    }
  }
}
