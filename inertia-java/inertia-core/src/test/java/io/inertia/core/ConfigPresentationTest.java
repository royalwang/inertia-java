package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfigPresentationTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest(
          "GET", URI.create("https://app.test/private?token=secret"), Map.of("x-inertia", "true"));

  @Test
  void defaultsAndConfigCopiesPreserveIndependentOptions() {
    var original = InertiaConfig.basic("v1", Set.of("Home"));
    assertTrue(original.exposeSharedPropKeys());
    assertEquals(request.url(), original.pageUrl(request));
    var configured =
        original.withUrlResolver(r -> "/public").withSharedPropKeys(false).withAllErrors(true);
    assertEquals("/public", configured.pageUrl(request));
    assertFalse(configured.exposeSharedPropKeys());
    assertTrue(configured.allErrors());
    assertEquals(request.url(), original.pageUrl(request));
    assertThrows(NullPointerException.class, () -> original.withUrlResolver(null));
  }

  @Test
  void resolverRunsOnceAndHidingMetadataKeepsSharedValuesAndErrors() throws Exception {
    var calls = new AtomicInteger();
    var config =
        new InertiaConfig(
                () -> "v1",
                "app",
                Set.of("Home"),
                RootView.minimal(),
                null,
                r -> Props.builder().put("auth.name", "Ada").build(),
                false,
                false)
            .withUrlResolver(
                r -> {
                  calls.incrementAndGet();
                  return "/public?page=2";
                })
            .withSharedPropKeys(false)
            .withAllErrors(true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var context = new InertiaContext(request, null, codec);
      context.withErrors(Map.of("name", List.of("Required", "Too short")));
      var outcome =
          renderer
              .render(context, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get();
      var page = codec.read(outcome.body());
      assertEquals("/public?page=2", page.path("url").asText());
      assertFalse(page.has("sharedProps"));
      assertEquals("Ada", page.at("/props/auth/name").asText());
      assertEquals(2, page.at("/props/errors/name").size());
      assertFalse(outcome.body().contains("secret"));
      assertEquals(1, calls.get());
      assertEquals("/private?token=secret", request.url());
      assertTrue(
          ProtocolPolicy.before(request, "v1")
              .orElseThrow()
              .header("X-Inertia-Location")
              .contains("private?token=secret"));
    }
  }

  @Test
  void invalidUrlRestoresDeliveryBeforeAnyQueriesOrSsr() throws Exception {
    for (String bad : Arrays.asList(null, "", "\r\n", "x".repeat(8193))) {
      var queries = new AtomicInteger();
      var ssr = new AtomicInteger();
      var roots = new AtomicInteger();
      var config =
          new InertiaConfig(
                  () -> "v1",
                  "app",
                  Set.of("Home"),
                  view -> {
                    roots.incrementAndGet();
                    return "root";
                  },
                  (page, req) -> {
                    ssr.incrementAndGet();
                    return CompletableFuture.completedFuture(new SsrGateway.Fallback("test"));
                  },
                  r -> Props.empty(),
                  false,
                  false)
              .withUrlResolver(r -> bad);
      var session = new MemorySessionStore();
      session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "Keep")));
      try (var executor = Executors.newFixedThreadPool(2)) {
        var renderer =
            new ResponseRenderer(
                config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
        var failed =
            renderer.render(
                new InertiaContext(
                    new InertiaRequest("GET", request.fullUrl(), Map.of()), session, codec),
                new InertiaResponse(
                    "Home",
                    Props.builder().put("query", Prop.lazy(queries::incrementAndGet)).build()));
        assertThrows(ExecutionException.class, () -> failed.toCompletableFuture().get());
        assertEquals(0, queries.get());
        assertEquals(0, ssr.get());
        assertEquals(0, roots.get());
        var delivery = session.beginPageDelivery();
        assertEquals("Keep", delivery.data().at("/inertia.flash_data/toast").asText());
        session.abortPageDelivery(delivery);
      }
    }
  }
}
