package io.inertia.core;

import static io.inertia.core.InertiaObserver.*;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PropDefinitionDiagnosticTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest(
          "GET",
          URI.create("https://app.test/?private=secret-url"),
          Map.of("X-Inertia", "true", "Cookie", "secret-cookie"));

  @Test
  void invalidPathsAndParentChildConflictsHaveTypedDiagnosticsAndNoValueLeak() {
    for (String path :
        Arrays.asList(null, "", "a..b", "a b", String.join(".", Collections.nCopies(33, "a")))) {
      var error =
          assertThrows(
              PropDefinitionException.class, () -> Props.builder().put(path, "secret-value"));
      assertInstanceOf(IllegalArgumentException.class, error);
      assertEquals(PropDefinitionException.Kind.INVALID_PATH, error.kind());
      assertEquals(Props.Source.DECLARED, error.firstSource());
      assertFalse(error.getMessage().contains("secret-value"));
    }
    var error =
        assertThrows(
            PropDefinitionException.class,
            () ->
                Props.overlay(
                    Props.from(
                        Props.Source.PAGE,
                        Props.builder().put("account.email", "secret-value").build()),
                    Props.from(
                        Props.Source.CONFIG_SHARED,
                        Props.builder().put("account", "secret-value").build())));
    assertEquals(PropDefinitionException.Kind.PARENT_CHILD_CONFLICT, error.kind());
    assertEquals("account", error.firstPath());
    assertEquals("account.email", error.secondPath());
    assertEquals(Props.Source.CONFIG_SHARED, error.firstSource());
    assertEquals(Props.Source.PAGE, error.secondSource());
    assertFalse(error.getMessage().contains("secret-value"));
  }

  @Test
  void exactOverridesAreInspectableImmutableAndKeepDeclarationOrder() {
    var global =
        Props.from(
            Props.Source.CONFIG_SHARED,
            Props.builder().put("first", 1).put("same", 1).put("last", 1).build());
    var local = Props.from(Props.Source.REQUEST_SHARED, Props.builder().put("same", 2).build());
    var shared = Props.overlay(global, local);
    var all =
        Props.overlay(
            shared, Props.from(Props.Source.PAGE, Props.builder().put("same", 3).build()));
    assertEquals(List.of("first", "same", "last"), all.entries().keySet().stream().toList());
    assertEquals(
        List.of(
            new Props.Override("same", Props.Source.CONFIG_SHARED, Props.Source.REQUEST_SHARED),
            new Props.Override("same", Props.Source.REQUEST_SHARED, Props.Source.PAGE)),
        all.overrides());
    assertThrows(UnsupportedOperationException.class, () -> all.overrides().clear());
    assertEquals(1, shared.overrides().size());
    assertTrue(global.overrides().isEmpty());
  }

  @Test
  void actualRenderKeepsPrecedenceAndSignalsErrorsOverrideWithoutQueriesOrPayload()
      throws Exception {
    var globalQueries = new AtomicInteger();
    var localQueries = new AtomicInteger();
    var pageQueries = new AtomicInteger();
    var events = new CopyOnWriteArrayList<Event>();
    var delivered = new CountDownLatch(5);
    InertiaObserver observer =
        InertiaObserver.combine(
            event -> {
              throw new IllegalStateException("bad logger");
            },
            event -> {
              events.add(event);
              delivered.countDown();
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              RootView.minimal(),
              null,
              req ->
                  Props.builder()
                      .put("private-schema-key", Prop.lazy(() -> globalQueries.incrementAndGet()))
                      .put("errors", Map.of("custom", "secret-error"))
                      .build(),
              false,
              false);
      var renderer =
          new ResponseRenderer(
              config,
              codec,
              new PropsResolver(
                  codec, executor, Duration.ofSeconds(1), 2, Clock.systemUTC(), observer),
              observer,
              "renderer");
      var context = new InertiaContext(request, null, codec);
      context.share("private-schema-key", Prop.lazy(() -> localQueries.incrementAndGet()));
      var outcome =
          renderer
              .render(
                  context,
                  new InertiaResponse(
                      "Home",
                      Props.builder()
                          .put(
                              "private-schema-key",
                              Prop.lazy(
                                  () -> {
                                    pageQueries.incrementAndGet();
                                    return "secret-value";
                                  }))
                          .build()))
              .toCompletableFuture()
              .get(2, TimeUnit.SECONDS);
      assertTrue(delivered.await(2, TimeUnit.SECONDS));
      assertEquals(200, outcome.status());
      assertEquals(
          "secret-value", codec.read(outcome.body()).at("/props/private-schema-key").asText());
      assertEquals("secret-error", codec.read(outcome.body()).at("/props/errors/custom").asText());
      assertEquals(0, globalQueries.get());
      assertEquals(0, localQueries.get());
      assertEquals(1, pageQueries.get());
      assertEquals(2, events.stream().filter(e -> e.reason() == Reason.PROP_OVERRIDE).count());
      assertEquals(1, events.stream().filter(e -> e.reason() == Reason.ERRORS_OVERRIDE).count());
      for (String secret :
          List.of(
              "private-schema-key", "secret-value", "secret-error", "secret-cookie", "secret-url"))
        assertFalse(codec.value(events).toString().contains(secret));
      assertTrue(events.stream().allMatch(e -> e.requestId().equals(request.requestId())));
    }
  }

  @Test
  void crossLayerConflictRejectsBeforeCallbacksAndRestoresReservedFlash() throws Exception {
    var calls = new AtomicInteger();
    var events = new CopyOnWriteArrayList<Event>();
    var complete = new CountDownLatch(3);
    InertiaObserver observer =
        event -> {
          events.add(event);
          complete.countDown();
        };
    var session = new MemorySessionStore();
    session.put(InertiaContext.FLASH, codec.value(Map.of("message", "retained-secret")));
    try (var executor = Executors.newSingleThreadExecutor()) {
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              RootView.minimal(),
              null,
              req ->
                  Props.builder().put("account", Prop.lazy(() -> calls.incrementAndGet())).build(),
              false,
              false);
      var renderer =
          new ResponseRenderer(
              config,
              codec,
              new PropsResolver(
                  codec, executor, Duration.ofSeconds(1), 1, Clock.systemUTC(), observer),
              observer,
              "renderer");
      var context = new InertiaContext(request, session, codec);
      context.share("account.email", "secret-value");
      var error =
          assertThrows(
              ExecutionException.class,
              () ->
                  renderer
                      .render(context, new InertiaResponse("Home", Props.empty()))
                      .toCompletableFuture()
                      .get(2, TimeUnit.SECONDS));
      var definition = assertInstanceOf(PropDefinitionException.class, error.getCause());
      assertEquals(Props.Source.CONFIG_SHARED, definition.firstSource());
      assertEquals(Props.Source.REQUEST_SHARED, definition.secondSource());
      assertTrue(complete.await(2, TimeUnit.SECONDS));
      assertEquals(0, calls.get());
      assertEquals("retained-secret", session.get(InertiaContext.FLASH).path("message").asText());
      assertEquals(
          Reason.PROP_DEFINITION,
          events.stream()
              .filter(e -> e.operation() == Operation.RENDER)
              .findFirst()
              .orElseThrow()
              .reason());
      assertTrue(events.stream().noneMatch(e -> e.operation() == Operation.PROPS));
      assertFalse(codec.value(events).toString().contains("account"));
    }
  }
}
