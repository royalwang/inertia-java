package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class CancellationContractTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest("GET", URI.create("http://app.test/"), Map.of());

  PropsResolver resolver(Executor executor, Duration deadline) {
    return new PropsResolver(codec, executor, deadline, 2);
  }

  @Test
  void deadlineInterruptsRunningCallbackAndWorkerCanServeNextRequest() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var entered = new CountDownLatch(1);
      var interrupted = new CountDownLatch(1);
      var resolver = resolver(executor, Duration.ofSeconds(1));
      var pending =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "slow",
                          Prop.lazy(
                              () -> {
                                entered.countDown();
                                try {
                                  new CountDownLatch(1).await();
                                  return "unreachable";
                                } catch (InterruptedException error) {
                                  interrupted.countDown();
                                  throw error;
                                }
                              }))
                      .build())
              .toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertInstanceOf(
          TimeoutException.class,
          assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS))
              .getCause());
      assertTrue(interrupted.await(2, TimeUnit.SECONDS));
      assertEquals(
          "ok",
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder().put("next", Prop.lazy(() -> "ok")).build())
              .toCompletableFuture()
              .get(2, TimeUnit.SECONDS)
              .props()
              .path("next")
              .asText());
    }
  }

  @Test
  void callerCancellationInterruptsRunningTaskAndQueuedCallbackNeverStarts() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var entered = new CountDownLatch(1);
      var interrupted = new CountDownLatch(1);
      var queuedCalls = new AtomicInteger();
      var pending =
          resolver(executor, Duration.ofSeconds(10))
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "running",
                          Prop.lazy(
                              () -> {
                                entered.countDown();
                                try {
                                  new CountDownLatch(1).await();
                                  return "unreachable";
                                } catch (InterruptedException error) {
                                  interrupted.countDown();
                                  throw error;
                                }
                              }))
                      .put("queued", Prop.lazy(() -> queuedCalls.incrementAndGet()))
                      .build())
              .toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertTrue(pending.cancel(true));
      assertTrue(pending.isCancelled());
      assertThrows(CancellationException.class, pending::join);
      assertTrue(interrupted.await(2, TimeUnit.SECONDS));
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      assertEquals(0, queuedCalls.get());
    }
  }

  @Test
  void cancelledQueuedWorkReleasesBoundedExecutorCapacityBeforeWorkerUnblocks() throws Exception {
    var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    var release = new CountDownLatch(1);
    try {
      var entered = new CountDownLatch(1);
      executor.execute(
          () -> {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var cancelledCalls = new AtomicInteger();
      var resolver = resolver(executor, Duration.ofSeconds(10));
      var cancelled =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put("queued", Prop.lazy(() -> cancelledCalls.incrementAndGet()))
                      .build())
              .toCompletableFuture();
      assertEquals(1, executor.getQueue().size());
      assertTrue(cancelled.cancel(true));
      assertEquals(
          0, executor.getQueue().size(), "Cancelled work must not occupy the only queue slot");
      var next =
          resolver
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder().put("next", Prop.lazy(() -> "ok")).build())
              .toCompletableFuture();
      assertEquals(1, executor.getQueue().size());
      release.countDown();
      assertEquals("ok", next.get(2, TimeUnit.SECONDS).props().path("next").asText());
      assertEquals(0, cancelledCalls.get());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void asyncSourceCancelledAndSupplierCreationUsesTheOwnedWorker() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var source = new CompletableFuture<Object>();
      var factory = new CountDownLatch(1);
      var cancelled = new CountDownLatch(1);
      var thread = new AtomicReference<Thread>();
      source.whenComplete(
          (value, error) -> {
            if (source.isCancelled()) cancelled.countDown();
          });
      var pending =
          resolver(executor, Duration.ofSeconds(10))
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "async",
                          Prop.async(
                              () -> {
                                thread.set(Thread.currentThread());
                                factory.countDown();
                                return source;
                              }))
                      .build())
              .toCompletableFuture();
      assertTrue(factory.await(2, TimeUnit.SECONDS));
      assertNotSame(Thread.currentThread(), thread.get());
      assertTrue(pending.cancel(true));
      assertTrue(cancelled.await(2, TimeUnit.SECONDS));
      assertFalse(source.complete("late"));
    }
  }

  @Test
  void blockingAsyncFactoryIsAlsoInterruptedAtDeadline() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var entered = new CountDownLatch(1);
      var interrupted = new CountDownLatch(1);
      var pending =
          resolver(executor, Duration.ofSeconds(1))
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "async",
                          Prop.async(
                              () -> {
                                entered.countDown();
                                try {
                                  new CountDownLatch(1).await();
                                } catch (InterruptedException error) {
                                  interrupted.countDown();
                                  throw new CompletionException(error);
                                }
                                return CompletableFuture.completedFuture("late");
                              }))
                      .build())
              .toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertInstanceOf(
          TimeoutException.class,
          assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS))
              .getCause());
      assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void aStageReturnedAfterCancellationIsCancelledImmediately() throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    var release = new CountDownLatch(1);
    try {
      var entered = new CountDownLatch(1);
      var late = new CompletableFuture<Object>();
      var pending =
          resolver(executor, Duration.ofSeconds(10))
              .resolve(
                  request,
                  "Home",
                  Props.empty(),
                  Props.builder()
                      .put(
                          "async",
                          Prop.async(
                              () -> {
                                entered.countDown();
                                while (release.getCount() > 0) {
                                  try {
                                    release.await();
                                  } catch (InterruptedException ignored) {
                                    /* Deliberately non-cooperative factory. */
                                  }
                                }
                                return late;
                              }))
                      .build())
              .toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertTrue(pending.cancel(true));
      release.countDown();
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      assertTrue(late.isCancelled());
      assertTrue(pending.isCancelled());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void completionAndCancellationCannotBothConsumeOrAbortTheSession() throws Exception {
    var completing = new CountDownLatch(1);
    var allowCommit = new CountDownLatch(1);
    var completed = new AtomicInteger();
    var aborted = new AtomicInteger();
    var memory = new MemorySessionStore();
    SessionStore session =
        new SessionStore() {
          public com.fasterxml.jackson.databind.JsonNode get(String key) {
            return memory.get(key);
          }

          public void put(String key, com.fasterxml.jackson.databind.JsonNode value) {
            memory.put(key, value);
          }

          public com.fasterxml.jackson.databind.JsonNode pull(String key) {
            return memory.pull(key);
          }

          public Delivery beginPageDelivery() {
            return memory.beginPageDelivery();
          }

          public void merge(com.fasterxml.jackson.databind.node.ObjectNode pending) {
            memory.merge(pending);
          }

          public void abortPageDelivery(Delivery delivery) {
            aborted.incrementAndGet();
            memory.abortPageDelivery(delivery);
          }

          public void completePageDelivery(Delivery delivery) {
            completing.countDown();
            try {
              if (!allowCommit.await(2, TimeUnit.SECONDS))
                throw new IllegalStateException("Commit fixture deadline");
            } catch (InterruptedException error) {
              throw new CompletionException(error);
            }
            completed.incrementAndGet();
            memory.completePageDelivery(delivery);
          }
        };
    try (var executor = Executors.newFixedThreadPool(2)) {
      var source = new CompletableFuture<SsrGateway.Result>();
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              view -> view.body(),
              (page, incoming) -> source,
              r -> Props.empty(),
              false,
              false);
      var pending =
          new ResponseRenderer(config, codec, resolver(executor, Duration.ofSeconds(10)))
              .render(
                  new InertiaContext(request, session, codec),
                  new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture();
      var finishing =
          executor.submit(() -> source.complete(new SsrGateway.Rendered("", "finished")));
      try {
        assertTrue(completing.await(2, TimeUnit.SECONDS));
        assertFalse(
            pending.cancel(true), "Cancellation cannot undo a completion that won arbitration");
      } finally {
        allowCommit.countDown();
      }
      assertTrue(finishing.get(2, TimeUnit.SECONDS));
      assertEquals("finished", pending.get(2, TimeUnit.SECONDS).body());
      assertEquals(1, completed.get());
      assertEquals(0, aborted.get());
    }
  }

  @Test
  void renderCancellationRestoresSessionRejectsLateWorkAndCancelsSsrSource() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var source = new CompletableFuture<SsrGateway.Result>();
      var entered = new CountDownLatch(1);
      var views = new AtomicInteger();
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              view -> {
                views.incrementAndGet();
                return view.body();
              },
              (page, incoming) -> {
                entered.countDown();
                return source;
              },
              r -> Props.empty(),
              false,
              false);
      var renderer =
          new ResponseRenderer(config, codec, resolver(executor, Duration.ofSeconds(10)));
      var session = new MemorySessionStore();
      session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
      var context = new InertiaContext(request, session, codec);
      var pending =
          renderer
              .render(context, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertTrue(pending.cancel(true));
      assertTrue(source.isCancelled());
      assertEquals("keep", session.get(InertiaContext.FLASH).path("toast").asText());
      assertThrows(IllegalStateException.class, () -> context.flash("late", "discard"));
      assertFalse(source.complete(new SsrGateway.Rendered("", "late")));
      assertEquals(0, views.get());
    }
  }

  @Test
  void cancellationCleanupFailureIsReportedWithoutSkippingOtherOwnedSources() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var factories = new CountDownLatch(2);
      var cleanup = new IllegalStateException("Provider cancellation failed");
      var refusing =
          new CompletableFuture<Object>() {
            @Override
            public boolean cancel(boolean interrupt) {
              throw cleanup;
            }
          };
      var normal = new CompletableFuture<Object>();
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              view -> view.body(),
              null,
              r -> Props.empty(),
              false,
              false);
      var pending =
          new ResponseRenderer(config, codec, resolver(executor, Duration.ofSeconds(10)))
              .render(
                  new InertiaContext(request, null, codec),
                  new InertiaResponse(
                      "Home",
                      Props.builder()
                          .put(
                              "refusing",
                              Prop.async(
                                  () -> {
                                    factories.countDown();
                                    return refusing;
                                  }))
                          .put(
                              "normal",
                              Prop.async(
                                  () -> {
                                    factories.countDown();
                                    return normal;
                                  }))
                          .build()))
              .toCompletableFuture();
      assertTrue(factories.await(2, TimeUnit.SECONDS));
      // A FIFO worker barrier confirms registration of both returned stages.
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      assertTrue(pending.cancel(true));
      assertTrue(normal.isCancelled());
      var failure = assertThrows(CancellationException.class, pending::join);
      assertTrue(Arrays.asList(failure.getSuppressed()).contains(cleanup));
      assertFalse(refusing.isDone());
      refusing.complete("late provider work");
      assertTrue(pending.isCancelled());
    }
  }

  @Test
  void cancelFalseAllowsNonCooperativeWorkToFinishWithoutLateResponseOrSessionCommit()
      throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    var release = new CountDownLatch(1);
    try {
      var entered = new CountDownLatch(1);
      var interrupted = new AtomicBoolean();
      var finished = new CountDownLatch(1);
      var views = new AtomicInteger();
      var serialized = new AtomicInteger();
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              view -> {
                views.incrementAndGet();
                return view.body();
              },
              null,
              r -> Props.empty(),
              false,
              false);
      var session = new MemorySessionStore();
      session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
      var context = new InertiaContext(request, session, codec);
      var pending =
          new ResponseRenderer(config, codec, resolver(executor, Duration.ofSeconds(10)))
              .render(
                  context,
                  new InertiaResponse(
                      "Home",
                      Props.builder()
                          .put(
                              "slow",
                              Prop.lazy(
                                  () -> {
                                    entered.countDown();
                                    try {
                                      release.await();
                                    } catch (InterruptedException error) {
                                      interrupted.set(true);
                                    }
                                    finished.countDown();
                                    return new LateValue(serialized);
                                  }))
                          .build()))
              .toCompletableFuture();
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertTrue(pending.cancel(false));
      release.countDown();
      assertTrue(finished.await(2, TimeUnit.SECONDS));
      executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
      assertFalse(interrupted.get());
      assertEquals(0, views.get());
      assertEquals(0, serialized.get(), "Cancelled callback results must not be serialized");
      assertTrue(pending.isCancelled());
      assertEquals("keep", session.get(InertiaContext.FLASH).path("toast").asText());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  static final class LateValue {
    private final AtomicInteger reads;

    LateValue(AtomicInteger reads) {
      this.reads = reads;
    }

    public String getValue() {
      reads.incrementAndGet();
      return "late";
    }
  }
}
