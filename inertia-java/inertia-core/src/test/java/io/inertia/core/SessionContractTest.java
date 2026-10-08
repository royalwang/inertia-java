package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class SessionContractTest {
  PageCodec codec = new PageCodec();
  MemorySessionStore session = new MemorySessionStore();
  ExecutorService executor;
  ResponseRenderer renderer;

  @BeforeEach
  void setup() {
    executor = Executors.newFixedThreadPool(2);
    renderer =
        new ResponseRenderer(
            InertiaConfig.basic("v1", Set.of("Home")),
            codec,
            new PropsResolver(codec, executor, Duration.ofMillis(250), 2));
  }

  @AfterEach
  void close() {
    executor.shutdownNow();
  }

  InertiaContext context() {
    return new InertiaContext(
        new InertiaRequest("GET", URI.create("http://app.test/"), Map.of("x-inertia", "true")),
        session,
        codec);
  }

  @Test
  void redirectFlashAndErrorsDeliveredOnce() throws Exception {
    var redirect = context();
    redirect.flash("toast", "Saved").withErrors(Map.of("name", "Required")).clearHistory();
    redirect.commitRedirect();
    var result =
        codec.read(
            renderer
                .render(context(), new InertiaResponse("Home", Props.empty()))
                .toCompletableFuture()
                .get()
                .body());
    assertEquals("Saved", result.at("/flash/toast").asText());
    assertEquals("Required", result.at("/props/errors/name").asText());
    assertTrue(result.path("clearHistory").asBoolean());
    var again =
        codec.read(
            renderer
                .render(context(), new InertiaResponse("Home", Props.empty()))
                .toCompletableFuture()
                .get()
                .body());
    assertFalse(again.has("flash"));
    assertTrue(again.at("/props/errors").isEmpty());
    assertFalse(again.has("clearHistory"));
  }

  @Test
  void failedRenderRestoresReservedFlashAndKeepsNewerWrites() throws Exception {
    session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "old", "other", "kept")));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var result =
        renderer
            .render(
                context(),
                new InertiaResponse(
                    "Home",
                    Props.builder()
                        .put(
                            "fails",
                            Prop.lazy(
                                () -> {
                                  entered.countDown();
                                  release.await();
                                  throw new IllegalStateException();
                                }))
                        .build()))
            .toCompletableFuture();
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    var next = context();
    next.flash("toast", "new");
    next.commitRedirect();
    release.countDown();
    assertThrows(ExecutionException.class, result::get);
    var restored =
        codec.read(
            renderer
                .render(context(), new InertiaResponse("Home", Props.empty()))
                .toCompletableFuture()
                .get()
                .body());
    assertEquals("new", restored.at("/flash/toast").asText());
    assertEquals("kept", restored.at("/flash/other").asText());
  }

  @Test
  void concurrentReservationsCannotReadSameSnapshot() {
    session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "once")));
    var first = session.beginPageDelivery();
    var second = session.beginPageDelivery();
    assertTrue(first.data().has(InertiaContext.FLASH));
    assertFalse(second.data().has(InertiaContext.FLASH));
    session.completePageDelivery(first);
    session.completePageDelivery(second);
    assertThrows(IllegalStateException.class, () -> session.abortPageDelivery(first));
  }

  @Test
  void callbackFlashReachesSamePageAndLateWritesFail() throws Exception {
    var context = context();
    var result =
        codec.read(
            renderer
                .render(
                    context,
                    new InertiaResponse(
                        "Home",
                        Props.builder()
                            .put(
                                "value",
                                Prop.lazy(
                                    () -> {
                                      context.flash("toast", "from callback");
                                      return 1;
                                    }))
                            .build()))
                .toCompletableFuture()
                .get()
                .body());
    assertEquals("from callback", result.at("/flash/toast").asText());
    assertThrows(IllegalStateException.class, () -> context.flash("late", 1));
  }

  @Test
  void synchronousSharedFailureAlsoRestoresSnapshot() {
    session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
    var bad =
        new InertiaConfig(
            () -> "v1",
            "app",
            Set.of("Home"),
            RootView.minimal(),
            null,
            r -> {
              throw new IllegalStateException();
            },
            false,
            false);
    var render =
        new ResponseRenderer(
            bad, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
    assertThrows(
        ExecutionException.class,
        () ->
            render
                .render(context(), new InertiaResponse("Home", Props.empty()))
                .toCompletableFuture()
                .get());
    assertEquals("keep", session.get(InertiaContext.FLASH).path("toast").asText());
  }

  @Test
  void abortDuringSsrRestoresFlashAndLateResultCannotConsumeIt() throws Exception {
    session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
    var pendingSsr = new CompletableFuture<SsrGateway.Result>();
    var entered = new CountDownLatch(1);
    var config =
        new InertiaConfig(
            () -> "v1",
            "app",
            Set.of("Home"),
            RootView.minimal(),
            (page, request) -> {
              entered.countDown();
              return pendingSsr;
            },
            r -> Props.empty(),
            false,
            false);
    var renderer =
        new ResponseRenderer(
            config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
    var context =
        new InertiaContext(
            new InertiaRequest("GET", URI.create("http://app.test/"), Map.of()), session, codec);
    var rendering =
        renderer.render(context, new InertiaResponse("Home", Props.empty())).toCompletableFuture();
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    context.abort();
    assertEquals("keep", session.get(InertiaContext.FLASH).path("toast").asText());
    assertThrows(IllegalStateException.class, () -> context.flash("late", 1));
    pendingSsr.complete(new SsrGateway.Rendered("", "<div id='app'>late</div>"));
    assertThrows(ExecutionException.class, rendering::get);
    assertEquals("keep", session.get(InertiaContext.FLASH).path("toast").asText());
    context.abort(); // Cleanup after a failed dispatch is idempotent.
  }
}
