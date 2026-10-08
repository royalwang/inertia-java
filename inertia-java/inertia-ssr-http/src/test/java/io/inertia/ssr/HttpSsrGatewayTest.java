package io.inertia.ssr;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.inertia.core.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class HttpSsrGatewayTest {
  HttpServer server;
  PageCodec codec = new PageCodec();
  AtomicReference<String> body =
      new AtomicReference<>(
          "{\"head\":[\"<title>Users</title>\"],\"body\":\"<div id='app'>Ada</div>\"}");
  AtomicReference<String> request = new AtomicReference<>();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/render",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          var bytes = body.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  SsrGateway.Result render(int limit) throws Exception {
    var gateway =
        new HttpSsrGateway(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/render"),
            Duration.ofMillis(100),
            Duration.ofSeconds(1),
            limit,
            1,
            codec);
    var node = codec.object();
    node.put("component", "Users/Index");
    node.set("props", codec.object());
    node.put("url", "/users");
    node.put("version", "v1");
    return gateway
        .render(
            new Page(node),
            new InertiaRequest("GET", URI.create("https://app.test/users"), Map.of()))
        .toCompletableFuture()
        .get(2, TimeUnit.SECONDS);
  }

  Page page() {
    var node =
        codec.object().put("component", "Users/Index").put("url", "/users").put("version", "v1");
    node.set("props", codec.object());
    return new Page(node);
  }

  @Test
  void postsBarePageAndReturnsHeadBody() throws Exception {
    var result = assertInstanceOf(SsrGateway.Rendered.class, render(1024));
    assertEquals("<title>Users</title>", result.head());
    assertEquals("Users/Index", codec.read(request.get()).path("component").asText());
    assertFalse(codec.read(request.get()).has("page"));
  }

  @Test
  void nonSuccessAndRedirectFallbackWithoutForwardingCredentials() throws Exception {
    var headers = new AtomicReference<com.sun.net.httpserver.Headers>();
    var targetCalls = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext(
        "/target",
        exchange -> {
          targetCalls.incrementAndGet();
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    server.removeContext("/render");
    server.createContext(
        "/render",
        exchange -> {
          headers.set(exchange.getRequestHeaders());
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().set("Location", "/target");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    var gateway =
        new HttpSsrGateway(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/render"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1024,
            1,
            codec);
    var result =
        gateway
            .render(
                page(),
                new InertiaRequest(
                    "GET",
                    URI.create("https://app.test/users"),
                    Map.of("Cookie", "private=secret", "Authorization", "Bearer secret")))
            .toCompletableFuture()
            .get(2, TimeUnit.SECONDS);
    assertEquals("http-status", assertInstanceOf(SsrGateway.Fallback.class, result).reason());
    assertNull(headers.get().getFirst("Cookie"));
    assertNull(headers.get().getFirst("Authorization"));
    assertEquals(0, targetCalls.get());
  }

  @Test
  void excludedRoutesNeverReachRenderer() throws Exception {
    var resolver =
        new SsrEndpointResolver(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/render"),
            null,
            null,
            false,
            java.util.List.of("users"));
    var gateway =
        new HttpSsrGateway(resolver, Duration.ofSeconds(1), Duration.ofSeconds(1), 1024, 1, codec);
    var result =
        gateway
            .render(
                page(), new InertiaRequest("GET", URI.create("https://app.test/users"), Map.of()))
            .toCompletableFuture()
            .get(2, TimeUnit.SECONDS);
    assertEquals(
        "excluded-or-unavailable", assertInstanceOf(SsrGateway.Fallback.class, result).reason());
    assertNull(request.get());
  }

  @Test
  void buildVerificationRequiresMatchingTextIdAndRetainsLegacyOptOut() throws Exception {
    var gateway =
        new HttpSsrGateway(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/render"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1024,
            1,
            codec,
            true);
    var incoming = new InertiaRequest("GET", URI.create("https://app.test/users"), Map.of());
    for (String id : new String[] {"\"other\"", "null", "42"}) {
      body.set("{\"head\":[],\"body\":\"<div>wrong build</div>\",\"buildId\":" + id + "}");
      assertEquals(
          "build-mismatch",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway.render(page(), incoming).toCompletableFuture().get(2, TimeUnit.SECONDS))
              .reason());
    }
    body.set("{\"head\":[],\"body\":\"<div>legacy</div>\"}");
    assertEquals(
        "build-mismatch",
        assertInstanceOf(
                SsrGateway.Fallback.class,
                gateway.render(page(), incoming).toCompletableFuture().get(2, TimeUnit.SECONDS))
            .reason());
    assertInstanceOf(SsrGateway.Rendered.class, render(1024));
    body.set("{\"head\":[],\"body\":\"<div>current</div>\",\"buildId\":\"v1\"}");
    assertInstanceOf(
        SsrGateway.Rendered.class,
        gateway.render(page(), incoming).toCompletableFuture().get(2, TimeUnit.SECONDS));
  }

  @Test
  void configuredRootRequiresMatchingRendererMetadata() throws Exception {
    var gateway =
        new HttpSsrGateway(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/render"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1024,
            1,
            codec,
            true,
            "portal");
    var incoming = new InertiaRequest("GET", URI.create("https://app.test/users"), Map.of());
    for (String root : new String[] {"\"app\"", "null", "42"}) {
      body.set("{\"head\":[],\"body\":\"wrong\",\"buildId\":\"v1\",\"rootId\":" + root + "}");
      assertEquals(
          "root-mismatch",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway.render(page(), incoming).toCompletableFuture().get(2, TimeUnit.SECONDS))
              .reason());
    }
    body.set("{\"head\":[],\"body\":\"current\",\"buildId\":\"v1\",\"rootId\":\"portal\"}");
    assertInstanceOf(
        SsrGateway.Rendered.class,
        gateway.render(page(), incoming).toCompletableFuture().get(2, TimeUnit.SECONDS));
  }

  @Test
  void nullInvalidAndOversizedBodiesFallback() throws Exception {
    body.set("null");
    assertEquals("warming-up", assertInstanceOf(SsrGateway.Fallback.class, render(1024)).reason());
    body.set("{\"head\":\"bad\",\"body\":1}");
    assertEquals(
        "invalid-response", assertInstanceOf(SsrGateway.Fallback.class, render(1024)).reason());
    body.set("not-json");
    assertEquals(
        "invalid-json", assertInstanceOf(SsrGateway.Fallback.class, render(1024)).reason());
    body.set("x".repeat(1024));
    assertInstanceOf(SsrGateway.Fallback.class, render(16));
  }
}
