package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CspNonceTest {
  String nonce = "server_nonce_0123456789";
  PageCodec codec = new PageCodec();

  @Test
  void rootIdRejectsUnsafeMarkupAndAcceptsConfiguredToken() {
    for (String bad : List.of("", "123", "app root", "app\" onclick=\"attack", "<app>"))
      assertThrows(IllegalArgumentException.class, () -> InertiaConfig.requireRootId(bad));
    assertEquals("portal-root_1", InertiaConfig.requireRootId("portal-root_1"));
  }

  @Test
  void validationRejectsMarkupAndHeaderIsNotTrustedMetadata() {
    for (String bad : List.of("short", "\" onload=\"attack", "x".repeat(300), "line\nbreak"))
      assertThrows(
          IllegalArgumentException.class,
          () -> new InertiaRequest("GET", URI.create("https://app.test/"), Map.of(), bad));
    assertNull(
        new InertiaRequest("GET", URI.create("https://app.test/"), Map.of("X-CSP-Nonce", nonce))
            .nonce());
    assertEquals(" nonce=\"" + nonce + "\"", CspNonce.attribute(nonce));
  }

  @Test
  void fallbackAndRootShareNonceWithoutPuttingItInPage() throws Exception {
    var captured = new AtomicReference<RootView.View>();
    var config =
        new InertiaConfig(
            () -> "v1",
            "portal",
            Set.of("Home"),
            view -> {
              captured.set(view);
              return view.body();
            },
            null,
            request -> Props.empty(),
            false,
            false);
    try (var executor = Executors.newFixedThreadPool(1)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 1));
      var request = new InertiaRequest("GET", URI.create("https://app.test/"), Map.of(), nonce);
      var html =
          renderer
              .render(request, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get(2, TimeUnit.SECONDS);
      assertTrue(html.body().contains("<script nonce=\"" + nonce + "\" data-page"));
      assertTrue(html.body().contains("<div id=\"portal\"></div>"));
      assertEquals(nonce, captured.get().nonce());
      assertFalse(captured.get().page().data().has("nonce"));
      var jsonRequest =
          new InertiaRequest(
              "GET", URI.create("https://app.test/"), Map.of("X-Inertia", "true"), nonce);
      var json =
          renderer
              .render(jsonRequest, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get(2, TimeUnit.SECONDS);
      assertFalse(json.body().contains(nonce));
    }
  }

  @Test
  void ssrFragmentsStayIntactAndRootReceivesTrustedNonce() throws Exception {
    var captured = new AtomicReference<RootView.View>();
    var config =
        new InertiaConfig(
            () -> "v1",
            "app",
            Set.of("Home"),
            view -> {
              captured.set(view);
              return view.body();
            },
            (page, request) ->
                CompletableFuture.completedFuture(
                    new SsrGateway.Rendered("<title>Home</title>", "<div id='app'>SSR</div>")),
            request -> Props.empty(),
            false,
            false);
    try (var executor = Executors.newFixedThreadPool(1)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 1));
      var result =
          renderer
              .render(
                  new InertiaRequest("GET", URI.create("https://app.test/"), Map.of(), nonce),
                  new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get(2, TimeUnit.SECONDS);
      assertEquals("<div id='app'>SSR</div>", result.body());
      assertEquals(nonce, captured.get().nonce());
      assertTrue(captured.get().ssr());
    }
  }
}
