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

  @Test
  void postsBarePageAndReturnsHeadBody() throws Exception {
    var result = assertInstanceOf(SsrGateway.Rendered.class, render(1024));
    assertEquals("<title>Users</title>", result.head());
    assertEquals("Users/Index", codec.read(request.get()).path("component").asText());
    assertFalse(codec.read(request.get()).has("page"));
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
