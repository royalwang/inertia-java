package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class HistoryOverridesTest {
  final PageCodec codec = new PageCodec();

  @Test
  void responseRequestGlobalPrecedenceAndFalseOmission() throws Exception {
    try (var executor = Executors.newFixedThreadPool(1)) {
      for (boolean global : new boolean[] {false, true}) {
        for (Boolean local : new Boolean[] {null, false, true}) {
          for (Boolean responseFlag : new Boolean[] {null, false, true}) {
            var config =
                new InertiaConfig(
                    () -> "v1",
                    "app",
                    Set.of("Home"),
                    v -> v.body(),
                    null,
                    r -> Props.empty(),
                    false,
                    global);
            var renderer =
                new ResponseRenderer(
                    config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 1));
            var context = context(true, null);
            if (local != null) context.encryptHistory(local);
            var response = new InertiaResponse("Home", Props.empty());
            if (responseFlag != null) response.encryptHistory(responseFlag);
            var page =
                codec.read(renderer.render(context, response).toCompletableFuture().get().body());
            boolean expected = responseFlag != null ? responseFlag : local != null ? local : global;
            assertEquals(expected, page.has("encryptHistory"));
            if (expected) assertTrue(page.path("encryptHistory").asBoolean());
            assertThrows(IllegalStateException.class, () -> context.encryptHistory(false));
          }
        }
      }
    }
  }

  @Test
  void callbackOverrideIsAppliedButNotPersistedAcrossRedirect() throws Exception {
    var session = new MemorySessionStore();
    var redirect = context(true, session);
    redirect.encryptHistory(true).clearHistory();
    redirect.commitRedirect();
    try (var executor = Executors.newFixedThreadPool(1)) {
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Home")),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 1));
      var context = context(true, session);
      var page =
          codec.read(
              renderer
                  .render(context, new InertiaResponse("Home", Props.empty()))
                  .toCompletableFuture()
                  .get()
                  .body());
      assertTrue(page.path("clearHistory").asBoolean());
      assertFalse(page.has("encryptHistory"));
      var callbackContext = context(true, session);
      var props =
          Props.builder()
              .put(
                  "value",
                  Prop.lazy(
                      () -> {
                        callbackContext.encryptHistory(true);
                        return "resolved";
                      }))
              .build();
      var next =
          codec.read(
              renderer
                  .render(callbackContext, new InertiaResponse("Home", props))
                  .toCompletableFuture()
                  .get()
                  .body());
      assertTrue(next.path("encryptHistory").asBoolean());
      assertFalse(next.has("clearHistory"));
    }
  }

  @Test
  void clearHistoryUsesOrAndSsrOverrideSkipsOnlyHtmlGateway() throws Exception {
    try (var executor = Executors.newFixedThreadPool(1)) {
      var calls = new AtomicInteger();
      var captured = new AtomicReference<Page>();
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              v -> {
                captured.set(v.page());
                return v.body();
              },
              (page, request) -> {
                calls.incrementAndGet();
                captured.set(page);
                return CompletableFuture.completedFuture(new SsrGateway.Rendered("", "SSR"));
              },
              r -> Props.empty(),
              false,
              true);
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 1));
      for (boolean json : new boolean[] {false, true}) {
        for (int clearSource = 0; clearSource < 3; clearSource++) {
          var session = new MemorySessionStore();
          if (clearSource == 0) session.put(InertiaContext.CLEAR, codec.value(true));
          var context = context(json, session);
          if (clearSource == 1) context.clearHistory();
          var response =
              new InertiaResponse("Home", Props.empty())
                  .clearHistory(clearSource == 2)
                  .withoutSsr();
          var outcome = renderer.render(context, response).toCompletableFuture().get();
          var page = json ? codec.read(outcome.body()) : captured.get().data();
          assertTrue(page.path("clearHistory").asBoolean());
          assertTrue(page.path("encryptHistory").asBoolean());
          if (!json) assertTrue(outcome.body().contains("<div id=\"app\"></div>"));
        }
      }
      assertEquals(0, calls.get());
      renderer
          .render(context(false, null), new InertiaResponse("Home", Props.empty()))
          .toCompletableFuture()
          .get();
      assertEquals(1, calls.get());
      assertTrue(captured.get().data().path("encryptHistory").asBoolean());
      renderer
          .render(context(true, null), new InertiaResponse("Home", Props.empty()))
          .toCompletableFuture()
          .get();
      assertEquals(1, calls.get());
    }
  }

  InertiaContext context(boolean json, SessionStore session) {
    return new InertiaContext(
        new InertiaRequest(
            "GET", URI.create("http://app.test/"), json ? Map.of("X-Inertia", "true") : Map.of()),
        session,
        codec);
  }
}
