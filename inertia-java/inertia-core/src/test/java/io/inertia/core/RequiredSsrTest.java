package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RequiredSsrTest {
  final PageCodec codec = new PageCodec();

  InertiaRequest request(boolean json) {
    return new InertiaRequest(
        "GET", URI.create("https://app.test/"), json ? Map.of("x-inertia", "true") : Map.of());
  }

  @Test
  void requiredFailureRestoresDeliveryAndNeverCallsRoot() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (String reason :
          List.of("disabled", "overloaded", "invalid-response", "SECRET_UNKNOWN")) {
        var session = new MemorySessionStore();
        var saved = codec.object();
        saved.set(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
        session.merge(saved);
        var roots = new AtomicInteger();
        var events = new ArrayList<InertiaObserver.Event>();
        var config =
            new InertiaConfig(
                () -> "v1",
                "app",
                Set.of("Home"),
                v -> {
                  roots.incrementAndGet();
                  return "html";
                },
                (p, r) -> CompletableFuture.completedFuture(new SsrGateway.Fallback(reason)),
                r -> Props.empty(),
                false,
                false);
        var renderer =
            new ResponseRenderer(
                config,
                codec,
                new PropsResolver(codec, executor, Duration.ofSeconds(1), 2),
                events::add,
                "test");
        var failure =
            assertThrows(
                ExecutionException.class,
                () ->
                    renderer
                        .render(
                            new InertiaContext(request(false), session, codec),
                            new InertiaResponse("Home", Props.empty()).requireSsr())
                        .toCompletableFuture()
                        .get());
        assertEquals(Observations.fallbackReason(reason), Observations.failureReason(failure));
        assertFalse(failure.getCause().toString().contains("SECRET"));
        assertEquals(0, roots.get());
        var restored = session.beginPageDelivery();
        assertEquals("keep", restored.data().at("/inertia.flash_data/toast").asText());
        session.abortPageDelivery(restored);
        assertEquals(
            1,
            events.stream()
                .filter(
                    e ->
                        e.operation() == InertiaObserver.Operation.SSR
                            && e.outcome() == InertiaObserver.Outcome.FALLBACK)
                .count());
      }
    }
  }

  @Test
  void successAndJsonAndExplicitCsrRetainExistingBehavior() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var calls = new AtomicInteger();
      var renderer =
          new ResponseRenderer(
              new InertiaConfig(
                  () -> "v1",
                  "app",
                  Set.of("Home"),
                  v -> v.body(),
                  (p, r) -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(
                        new SsrGateway.Rendered("", "SSR CONTENT"));
                  },
                  r -> Props.empty(),
                  false,
                  false),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      assertEquals(
          "SSR CONTENT",
          renderer
              .render(request(false), new InertiaResponse("Home", Props.empty()).requireSsr())
              .toCompletableFuture()
              .get()
              .body());
      assertEquals(
          "Home",
          codec
              .read(
                  renderer
                      .render(
                          request(true), new InertiaResponse("Home", Props.empty()).requireSsr())
                      .toCompletableFuture()
                      .get()
                      .body())
              .path("component")
              .asText());
      var csr = new InertiaResponse("Home", Props.empty()).requireSsr().withoutSsr();
      assertFalse(csr.ssrRequired());
      assertFalse(csr.ssr());
      assertTrue(
          renderer
              .render(request(false), csr)
              .toCompletableFuture()
              .get()
              .body()
              .contains("data-page"));
      assertEquals(1, calls.get());
      assertTrue(
          new InertiaResponse("Home", Props.empty()).withoutSsr().requireSsr().ssrRequired());
    }
  }

  @Test
  void missingGatewayAndExceptionalGatewayFailRequiredButDefaultStillFallsBack() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (SsrGateway gateway :
          Arrays.asList(
              null,
              (SsrGateway) (p, r) -> null,
              (SsrGateway) (p, r) -> CompletableFuture.completedFuture(null),
              (SsrGateway) (p, r) -> CompletableFuture.failedFuture(new TimeoutException("SECRET")),
              (SsrGateway)
                  (p, r) -> {
                    throw new IllegalStateException("SECRET");
                  })) {
        var renderer =
            new ResponseRenderer(
                new InertiaConfig(
                    () -> "v1",
                    "app",
                    Set.of("Home"),
                    RootView.minimal(),
                    gateway,
                    r -> Props.empty(),
                    false,
                    false),
                codec,
                new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
        var error =
            assertThrows(
                ExecutionException.class,
                () ->
                    renderer
                        .render(
                            request(false), new InertiaResponse("Home", Props.empty()).requireSsr())
                        .toCompletableFuture()
                        .get());
        Throwable cause = error;
        while (!(cause instanceof SsrRequiredException) && cause.getCause() != null)
          cause = cause.getCause();
        assertInstanceOf(SsrRequiredException.class, cause);
        assertEquals("Required server rendering is unavailable", cause.getMessage());
        if (gateway == null)
          assertTrue(
              renderer
                  .render(request(false), new InertiaResponse("Home", Props.empty()))
                  .toCompletableFuture()
                  .get()
                  .body()
                  .contains("data-page"));
      }
    }
  }

  @Test
  void cancellationStillCancelsOwnedGatewayAndRestoresReservation() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var session = new MemorySessionStore();
      var saved = codec.object();
      saved.set(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
      session.merge(saved);
      var raw = new CompletableFuture<SsrGateway.Result>();
      var dispatched = new CountDownLatch(1);
      var roots = new AtomicInteger();
      var renderer =
          new ResponseRenderer(
              new InertiaConfig(
                  () -> "v1",
                  "app",
                  Set.of("Home"),
                  v -> {
                    roots.incrementAndGet();
                    return "html";
                  },
                  (p, r) -> {
                    dispatched.countDown();
                    return raw;
                  },
                  r -> Props.empty(),
                  false,
                  false),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var pending =
          renderer
              .render(
                  new InertiaContext(request(false), session, codec),
                  new InertiaResponse("Home", Props.empty()).requireSsr())
              .toCompletableFuture();
      assertTrue(dispatched.await(1, TimeUnit.SECONDS));
      assertTrue(pending.cancel(true));
      assertTrue(raw.isCancelled());
      assertEquals(0, roots.get());
      var restored = session.beginPageDelivery();
      assertEquals("keep", restored.data().at("/inertia.flash_data/toast").asText());
      session.abortPageDelivery(restored);
    }
  }

  @Test
  void wrappedCancellationIsNotServiceUnavailability() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              new InertiaConfig(
                  () -> "v1",
                  "app",
                  Set.of("Home"),
                  RootView.minimal(),
                  (p, r) ->
                      CompletableFuture.failedFuture(
                          new CompletionException(new CancellationException())),
                  r -> Props.empty(),
                  false,
                  false),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var error =
          assertThrows(
              Exception.class,
              () ->
                  renderer
                      .render(
                          request(false), new InertiaResponse("Home", Props.empty()).requireSsr())
                      .toCompletableFuture()
                      .get());
      assertEquals(InertiaObserver.Reason.CANCELLED, Observations.failureReason(error));
      for (Throwable current = error; current != null; current = current.getCause())
        assertFalse(current instanceof SsrRequiredException);
    }
  }
}
