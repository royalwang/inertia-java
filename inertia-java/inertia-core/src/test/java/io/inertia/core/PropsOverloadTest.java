package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PropsOverloadTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest("GET", URI.create("https://app.test/"), Map.of());

  @Test
  void globalQueueRejectionFailsRenderWhileCancellationFreesOnlyItsOwnSlot() throws Exception {
    var executor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.AbortPolicy());
    var firstStarted = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var queuedCalls = new AtomicInteger();
    var rejectedCalls = new AtomicInteger();
    var fourthCalls = new AtomicInteger();
    var roots = new AtomicInteger();
    var ssrCalls = new AtomicInteger();
    var events = new CopyOnWriteArrayList<InertiaObserver.Event>();
    var observed = new CountDownLatch(5);
    InertiaObserver observer =
        event -> {
          events.add(event);
          observed.countDown();
        };
    try {
      var resolver =
          new PropsResolver(
              codec, executor, Duration.ofSeconds(10), 1, Clock.systemUTC(), observer);
      var first =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "held",
                          Prop.lazy(
                              () -> {
                                firstStarted.countDown();
                                releaseFirst.await();
                                return "first";
                              }))
                      .build())
              .toCompletableFuture();
      assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
      var second =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder().put("queued", Prop.lazy(queuedCalls::incrementAndGet)).build())
              .toCompletableFuture();
      assertEquals(1, executor.getQueue().size());
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              view -> {
                roots.incrementAndGet();
                return view.body();
              },
              (page, req) -> {
                ssrCalls.incrementAndGet();
                return CompletableFuture.completedFuture(new SsrGateway.Fallback("disabled"));
              },
              req -> Props.empty(),
              false,
              false);
      var renderer = new ResponseRenderer(config, codec, resolver, observer, "renderer");
      var rejected =
          renderer
              .render(
                  request,
                  new InertiaResponse(
                      "Home",
                      Props.builder()
                          .put("rejected", Prop.lazy(rejectedCalls::incrementAndGet))
                          .build()))
              .toCompletableFuture();
      var failure = assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
      assertInstanceOf(RejectedExecutionException.class, failure.getCause());
      assertEquals(0, roots.get());
      assertEquals(0, ssrCalls.get());
      assertEquals(0, rejectedCalls.get());
      assertFalse(first.isDone());
      assertFalse(second.isDone());
      assertTrue(second.cancel(true));
      assertTrue(executor.getQueue().isEmpty());
      var fourth =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder().put("recovered", Prop.lazy(fourthCalls::incrementAndGet)).build())
              .toCompletableFuture();
      assertFalse(first.isDone());
      releaseFirst.countDown();
      assertEquals("first", first.get(2, TimeUnit.SECONDS).props().path("held").asText());
      assertEquals(1, fourth.get(2, TimeUnit.SECONDS).props().path("recovered").asInt());
      assertEquals(0, queuedCalls.get());
      assertEquals(1, fourthCalls.get());
      assertTrue(observed.await(2, TimeUnit.SECONDS));
      assertEquals(
          2, events.stream().filter(e -> e.reason() == InertiaObserver.Reason.OVERLOADED).count());
      assertEquals(
          1, events.stream().filter(e -> e.outcome() == InertiaObserver.Outcome.CANCELLED).count());
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void unresolvedAsyncRetainsRequestPermitAndRejectionCancelsOriginalSource() throws Exception {
    var source = new CompletableFuture<String>();
    var callbacks = new AtomicInteger();
    var events = new CopyOnWriteArrayList<InertiaObserver.Event>();
    var observed = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var resolver =
          new PropsResolver(
              codec,
              executor,
              Duration.ofSeconds(5),
              1,
              Clock.systemUTC(),
              event -> {
                events.add(event);
                observed.countDown();
              });
      var pending =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "held",
                          Prop.async(
                              () -> {
                                callbacks.incrementAndGet();
                                return source;
                              }))
                      .put("rejected", Prop.lazy(callbacks::incrementAndGet))
                      .build())
              .toCompletableFuture();
      var error = assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
      assertInstanceOf(PropResolutionException.class, error.getCause());
      assertInstanceOf(
          RejectedExecutionException.class,
          assertInstanceOf(PropResolutionException.class, error.getCause().getCause()).getCause());
      assertEquals(1, callbacks.get());
      assertTrue(source.isCancelled());
      assertTrue(observed.await(2, TimeUnit.SECONDS));
      assertEquals(InertiaObserver.Reason.OVERLOADED, events.getFirst().reason());
      var next =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder().put("next", Prop.lazy(() -> 7)).build())
              .toCompletableFuture();
      assertEquals(7, next.get(2, TimeUnit.SECONDS).props().path("next").asInt());
    }
  }

  @Test
  void invalidScrollResultFailsBeforeWaitingForUnresolvedSibling() throws Exception {
    var source = new CompletableFuture<String>();
    try (var executor = Executors.newSingleThreadExecutor()) {
      var resolver = new PropsResolver(codec, executor, Duration.ofSeconds(5), 2);
      var pending =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put("held", Prop.async(() -> source))
                      .put("scroll", Prop.scrollWith(() -> Map.of("wrong", "type")))
                      .build())
              .toCompletableFuture();
      var failure = assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
      var prop = assertInstanceOf(PropResolutionException.class, failure.getCause());
      assertEquals("scroll", prop.path());
      assertInstanceOf(IllegalArgumentException.class, prop.getCause());
      assertTrue(source.isCancelled());
    }
  }

  @Test
  void rescuedSiblingFailureDoesNotCancelHealthyAsyncOrChangeMetadataOrder() throws Exception {
    var source = new CompletableFuture<String>();
    var entered = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var resolver = new PropsResolver(codec, executor, Duration.ofSeconds(5), 2);
      var props =
          Props.builder()
              .put(
                  "rescued",
                  Prop.defer(
                          () -> {
                            throw new IllegalStateException("private error");
                          })
                      .rescue())
              .put(
                  "healthy",
                  Prop.async(
                      () -> {
                        entered.countDown();
                        return source;
                      }))
              .build();
      var partial =
          new InertiaRequest(
              "GET",
              request.fullUrl(),
              Map.of(
                  "X-Inertia",
                  "true",
                  "X-Inertia-Partial-Component",
                  "Home",
                  "X-Inertia-Partial-Data",
                  "rescued,healthy"));
      var pending = resolver.resolve(partial, "Home", Props.empty(), props).toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      assertFalse(pending.isDone());
      assertFalse(source.isCancelled());
      source.complete("healthy value");
      var resolved = pending.get(2, TimeUnit.SECONDS);
      assertEquals("healthy value", resolved.props().path("healthy").asText());
      assertFalse(resolved.props().has("rescued"));
      assertEquals(codec.value(List.of("rescued")), resolved.metadata().get("rescuedProps"));
    }
  }
}
