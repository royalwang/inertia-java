package io.inertia.ssr;

import static io.inertia.core.InertiaObserver.*;
import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class HttpObservationTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest(
          "GET",
          URI.create("https://app.test/?private=url-secret"),
          Map.of("Cookie", "cookie-secret"));

  Page page() {
    var value =
        codec.object().put("component", "Home").put("version", "v1").put("url", "/private-secret");
    value.set("props", codec.object().put("private", "props-secret"));
    return new Page(value);
  }

  HttpSsrGateway gateway(URI uri, Duration timeout, int limit, List<Event> events) {
    return new HttpSsrGateway(
        uri,
        Duration.ofSeconds(1),
        timeout,
        limit,
        1,
        codec,
        false,
        null,
        InertiaObserver.combine(
            event -> {
              throw new IllegalStateException("logger-secret");
            },
            events::add),
        "primary");
  }

  void awaitEvents(List<Event> events, int count) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (events.size() < count && System.nanoTime() < deadline) Thread.sleep(1);
    assertEquals(count, events.size());
  }

  @Test
  void realBodyDeadlineAndLimitHaveDistinctReasonsWithoutChangingFallbackContract()
      throws Exception {
    for (String mode : List.of("stall", "oversize")) {
      var events = new CopyOnWriteArrayList<Event>();
      try (var server = new HttpSsrFailureTest.Renderer()) {
        server.mode.set(mode);
        var gateway =
            gateway(
                server.uri(), Duration.ofSeconds(1), mode.equals("oversize") ? 128 : 1024, events);
        var result = gateway.render(page(), request).toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(
            "transport-or-timeout", assertInstanceOf(SsrGateway.Fallback.class, result).reason());
        assertTrue(server.closed.await(2, TimeUnit.SECONDS));
        awaitEvents(events, 1);
        assertEquals(Outcome.FALLBACK, events.getFirst().outcome());
        assertEquals(
            mode.equals("oversize") ? Reason.RESPONSE_LIMIT : Reason.TIMEOUT,
            events.getFirst().reason());
        server.mode.set("valid");
        assertInstanceOf(
            SsrGateway.Rendered.class,
            gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS));
        awaitEvents(events, 2);
        assertEquals(Outcome.SUCCESS, events.getLast().outcome());
        assertEquals(200, events.getLast().status());
        assertEquals(2, server.calls.get());
      }
    }
  }

  @Test
  void refusedConnectionAndHttpStatusAreDifferentAndEventsContainNoSensitiveData()
      throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    URI closed;
    try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      closed = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/private-endpoint");
    }
    assertInstanceOf(
        SsrGateway.Fallback.class,
        gateway(closed, Duration.ofSeconds(2), 1024, events)
            .render(page(), request)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS));
    awaitEvents(events, 1);
    assertEquals(Reason.CONNECTION, events.getFirst().reason());
    try (var server = new HttpSsrFailureTest.Renderer()) {
      server.mode.set("status");
      assertEquals(
          "http-status",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway(server.uri(), Duration.ofSeconds(2), 1024, events)
                      .render(page(), request)
                      .toCompletableFuture()
                      .get(3, TimeUnit.SECONDS))
              .reason());
      awaitEvents(events, 2);
      assertEquals(Reason.HTTP_STATUS, events.getLast().reason());
      assertEquals(503, events.getLast().status());
    }
    assertTrue(
        events.stream()
            .allMatch(
                e ->
                    e.operation() == Operation.SSR_HTTP
                        && e.requestId().equals(request.requestId())
                        && e.endpointId().equals("primary")));
    String json = codec.value(events).toString();
    for (String secret :
        List.of(
            "127.0.0.1",
            "private-endpoint",
            "url-secret",
            "cookie-secret",
            "props-secret",
            "private-secret",
            "logger-secret",
            "OK</div>")) assertFalse(json.contains(secret));
  }

  @Test
  void cancellationWinsOnceAndOverloadDoesNotDispatch() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    try (var server = new HttpSsrFailureTest.Renderer()) {
      var gateway = gateway(server.uri(), Duration.ofSeconds(10), 1024, events);
      var pending = gateway.render(page(), request).toCompletableFuture();
      assertTrue(server.entered.await(2, TimeUnit.SECONDS));
      assertEquals(
          "overloaded",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway.render(page(), request).toCompletableFuture().get(1, TimeUnit.SECONDS))
              .reason());
      assertTrue(pending.cancel(true));
      assertTrue(server.closed.await(2, TimeUnit.SECONDS));
      awaitEvents(events, 2);
      assertEquals(Reason.OVERLOADED, events.getFirst().reason());
      assertEquals(Outcome.CANCELLED, events.getLast().outcome());
      assertEquals(Reason.CANCELLED, events.getLast().reason());
      server.mode.set("valid");
      assertInstanceOf(
          SsrGateway.Rendered.class,
          gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS));
      awaitEvents(events, 3);
      assertEquals(2, server.calls.get());
    }
  }

  @Test
  void truncatedResponseIsTransportFailureAndDecodeFallbackReasonsAreBounded() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    try (var server = new HttpSsrFailureTest.Renderer()) {
      server.mode.set("truncated");
      assertEquals(
          "transport-or-timeout",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway(server.uri(), Duration.ofSeconds(2), 1024, events)
                      .render(page(), request)
                      .toCompletableFuture()
                      .get(3, TimeUnit.SECONDS))
              .reason());
      awaitEvents(events, 1);
      assertEquals(Reason.TRANSPORT, events.getFirst().reason());
      assertEquals(1, server.calls.get());
    }
    record Scenario(String body, boolean build, String root, Reason reason) {}
    var scenarios =
        List.of(
            new Scenario("{bad", false, null, Reason.INVALID_JSON),
            new Scenario("null", false, null, Reason.WARMING_UP),
            new Scenario("{\"head\":[],\"body\":\"\"}", false, null, Reason.INVALID_RESPONSE),
            new Scenario(
                "{\"head\":[],\"body\":\"secret-renderer-body\",\"buildId\":\"old\"}",
                true,
                null,
                Reason.BUILD_MISMATCH),
            new Scenario(
                "{\"head\":[],\"body\":\"secret-renderer-body\",\"rootId\":\"wrong\"}",
                false,
                "app",
                Reason.ROOT_MISMATCH));
    for (var scenario : scenarios) {
      var server =
          com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/render",
          exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = scenario.body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (exchange) {
              exchange.getResponseBody().write(bytes);
            }
          });
      server.start();
      try {
        var observed = new CopyOnWriteArrayList<Event>();
        var gateway =
            new HttpSsrGateway(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/render"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1024,
                1,
                codec,
                scenario.build(),
                scenario.root(),
                observed::add,
                "primary");
        assertInstanceOf(
            SsrGateway.Fallback.class,
            gateway.render(page(), request).toCompletableFuture().get(3, TimeUnit.SECONDS));
        awaitEvents(observed, 1);
        assertEquals(scenario.reason(), observed.getFirst().reason());
        assertEquals(200, observed.getFirst().status());
        assertFalse(codec.value(observed).toString().contains("secret-renderer-body"));
      } finally {
        server.stop(0);
      }
    }
  }

  @Test
  void excludedEndpointIsObservedWithoutTransportAndUnsafeIdentifierRejected() throws Exception {
    var events = new ArrayList<Event>();
    var endpoints =
        new SsrEndpointResolver(
            URI.create("http://127.0.0.1:1/render"), null, null, false, List.of("*"));
    var gateway =
        new HttpSsrGateway(
            endpoints,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1024,
            1,
            codec,
            false,
            null,
            events::add,
            "primary");
    assertEquals(
        "excluded-or-unavailable",
        assertInstanceOf(
                SsrGateway.Fallback.class,
                gateway.render(page(), request).toCompletableFuture().get())
            .reason());
    assertEquals(1, events.size());
    assertEquals(Reason.EXCLUDED_OR_UNAVAILABLE, events.getFirst().reason());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new HttpSsrGateway(
                endpoints,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1024,
                1,
                codec,
                false,
                null,
                events::add,
                "http://private.test"));
  }
}
