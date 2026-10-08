package io.inertia.core;

import static io.inertia.core.InertiaObserver.*;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObservationContractTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest(
          "GET",
          URI.create("https://example.test/?secret=url-secret"),
          Map.of("Cookie", "cookie-secret", "X-Request-Id", "untrusted-secret"));

  @Test
  void renderEventsCorrelateWithoutPayloadAndUnknownFallbackIsBounded() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    var delivered = new CountDownLatch(5);
    InertiaObserver observer =
        event -> {
          events.add(event);
          delivered.countDown();
        };
    try (var executor = Executors.newSingleThreadExecutor()) {
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              v -> v.body(),
              (page, req) ->
                  CompletableFuture.completedFuture(new SsrGateway.Fallback("remote-secret")),
              req -> Props.empty(),
              false,
              false);
      var resolver =
          new PropsResolver(codec, executor, Duration.ofSeconds(1), 1, Clock.systemUTC(), observer);
      var renderer = new ResponseRenderer(config, codec, resolver, observer, "primary");
      var context = new InertiaContext(request, new MemorySessionStore(), codec);
      context.flash("message", "flash-secret");
      assertEquals(
          200,
          renderer
              .render(
                  context,
                  new InertiaResponse(
                      "Home", Props.builder().put("secret", "props-secret").build()))
              .toCompletableFuture()
              .get()
              .status());
      assertTrue(delivered.await(2, TimeUnit.SECONDS));
      assertEquals(
          Set.of(
              Operation.PROPS,
              Operation.SSR,
              Operation.RENDER,
              Operation.SESSION_BEGIN,
              Operation.SESSION_COMPLETE),
          new HashSet<>(events.stream().map(Event::operation).toList()));
      assertEquals(5, events.size());
      assertTrue(
          events.stream()
              .allMatch(e -> e.requestId().equals(request.requestId()) && e.elapsedNanos() >= 0));
      var ssr =
          events.stream().filter(e -> e.operation() == Operation.SSR).findFirst().orElseThrow();
      assertEquals(Outcome.FALLBACK, ssr.outcome());
      assertEquals(Reason.UNKNOWN, ssr.reason());
      assertEquals("primary", ssr.endpointId());
      String json = codec.value(events).toString();
      for (String secret :
          List.of(
              "url-secret",
              "cookie-secret",
              "untrusted-secret",
              "remote-secret",
              "flash-secret",
              "props-secret")) assertFalse(json.contains(secret));
    }
  }

  @Test
  void observerFailureCannotChangeBusinessOrSuppressOtherObservers() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    var delivered = new CountDownLatch(3);
    var observer =
        InertiaObserver.combine(
            event -> {
              throw new IllegalStateException("broken logger");
            },
            event -> {
              events.add(event);
              delivered.countDown();
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var resolver =
          new PropsResolver(codec, executor, Duration.ofSeconds(1), 1, Clock.systemUTC(), observer);
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Home")), codec, resolver, observer, "primary");
      assertEquals(
          200,
          renderer
              .render(request, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get()
              .status());
      assertTrue(delivered.await(2, TimeUnit.SECONDS));
      assertEquals(3, events.size());
      assertEquals(
          Reason.DISABLED,
          events.stream()
              .filter(e -> e.operation() == Operation.SSR)
              .findFirst()
              .orElseThrow()
              .reason());
    }
  }

  @Test
  void cancellationAndLateCompletionEmitOnce() throws Exception {
    var events = new CopyOnWriteArrayList<Event>();
    var source = new CompletableFuture<String>();
    var started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var resolver =
          new PropsResolver(
              codec, executor, Duration.ofSeconds(5), 1, Clock.systemUTC(), events::add);
      var pending =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "slow",
                          Prop.async(
                              () -> {
                                started.countDown();
                                return source;
                              }))
                      .build())
              .toCompletableFuture();
      assertTrue(started.await(2, TimeUnit.SECONDS));
      assertTrue(pending.cancel(true));
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      assertFalse(source.complete("late"));
      assertEquals(1, events.size());
      assertEquals(Outcome.CANCELLED, events.getFirst().outcome());
      var span = Observations.start(events::add, Operation.SSR, request, "Home", "primary");
      span.failure(new CompletionException(new TimeoutException()));
      span.success();
      assertEquals(2, events.size());
      assertEquals(Outcome.TIMEOUT, events.getLast().outcome());
    }
  }

  @Test
  void redirectSessionFailurePreservesOriginalAndReportsMerge() {
    var events = new ArrayList<Event>();
    var failure = new IllegalStateException("session-secret");
    SessionStore store =
        new SessionStore() {
          @Override
          public com.fasterxml.jackson.databind.JsonNode get(String key) {
            return null;
          }

          @Override
          public void put(String key, com.fasterxml.jackson.databind.JsonNode value) {}

          @Override
          public com.fasterxml.jackson.databind.JsonNode pull(String key) {
            return null;
          }

          @Override
          public Delivery beginPageDelivery() {
            throw new AssertionError("Unexpected begin");
          }

          @Override
          public void completePageDelivery(Delivery delivery) {
            throw new AssertionError("Unexpected complete");
          }

          @Override
          public void abortPageDelivery(Delivery delivery) {
            throw new AssertionError("Unexpected abort");
          }

          @Override
          public void merge(com.fasterxml.jackson.databind.node.ObjectNode pending) {
            throw failure;
          }
        };
    var context = new InertiaContext(request, store, codec, events::add);
    context.flash("message", "secret");
    assertSame(failure, assertThrows(IllegalStateException.class, context::commitRedirect));
    assertEquals(1, events.size());
    assertEquals(Operation.SESSION_MERGE, events.getFirst().operation());
    assertEquals(Outcome.FAILURE, events.getFirst().outcome());
    assertFalse(codec.value(events).toString().contains("session-secret"));
  }
}
