package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import org.junit.jupiter.api.*;

class OnceTtlContractTest {
  private final PageCodec codec = new PageCodec();

  @TestFactory
  Stream<DynamicTest> fixedClockSemanticCases() throws Exception {
    try (var input = getClass().getResourceAsStream("/compatibility/java_once_ttl_cases.json")) {
      assertNotNull(input);
      var cases = codec.read(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      assertEquals(9, cases.size());
      var names = new HashSet<String>();
      return StreamSupport.stream(cases.spliterator(), false)
          .map(
              c -> {
                String name = c.path("name").asText();
                assertTrue(names.add(name));
                return DynamicTest.dynamicTest(
                    name,
                    () -> {
                      try (var executor = Executors.newFixedThreadPool(2)) {
                        var calls = new AtomicInteger();
                        var prop =
                            Prop.lazy(
                                    () -> {
                                      calls.incrementAndGet();
                                      return 7;
                                    })
                                .onceAs("ttl-cache");
                        if (c.has("ttlMillis"))
                          prop = prop.until(Duration.ofMillis(c.path("ttlMillis").asLong()));
                        if (c.path("fresh").asBoolean()) prop = prop.fresh();
                        var headers = new LinkedHashMap<String, String>();
                        c.path("headers")
                            .fields()
                            .forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
                        var resolver =
                            new PropsResolver(
                                codec,
                                executor,
                                Duration.ofSeconds(1),
                                2,
                                Clock.fixed(
                                    Instant.ofEpochMilli(1_700_000_000_999L), ZoneOffset.UTC));
                        var result =
                            resolver
                                .resolve(
                                    new InertiaRequest(
                                        "GET", URI.create("https://app.test/ttl"), headers),
                                    "Home",
                                    Props.empty(),
                                    Props.builder().put("catalog", prop).build())
                                .toCompletableFuture()
                                .get();
                        assertEquals(c.path("calls").asInt(), calls.get());
                        assertEquals(c.path("value").asBoolean(), result.props().has("catalog"));
                        if (result.props().has("catalog"))
                          assertEquals(7, result.props().path("catalog").asInt());
                        assertEquals(
                            c.path("metadata").asBoolean(), result.metadata().has("onceProps"));
                        if (c.path("metadata").asBoolean()) {
                          assertEquals(
                              "catalog",
                              result.metadata().at("/onceProps/ttl-cache/prop").asText());
                          var expires = result.metadata().at("/onceProps/ttl-cache/expiresAt");
                          if (!c.has("ttlMillis")) assertTrue(expires.isNull());
                          else {
                            // Independent explicit boundaries: floor wall time and TTL separately.
                            long expected =
                                switch (c.path("ttlMillis").asInt()) {
                                  case 0, 999 -> 1_700_000_000_000L;
                                  case 1500 -> 1_700_000_001_000L;
                                  case 60000 -> 1_700_000_060_000L;
                                  default ->
                                      throw new AssertionError(
                                          "Add an explicit TTL boundary expectation");
                                };
                            assertEquals(expected, expires.asLong());
                          }
                        }
                      }
                    });
              });
    }
  }

  @Test
  void overflowFailsDeliveryAndRestoresSessionRatherThanWrappingExpiry() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var session = new MemorySessionStore();
      var saved = codec.object();
      saved.set(InertiaContext.FLASH, codec.value(Map.of("message", "keep")));
      session.merge(saved);
      var roots = new AtomicInteger();
      var renderer =
          new ResponseRenderer(
              new InertiaConfig(
                  () -> "v1",
                  "app",
                  Set.of("Home"),
                  view -> {
                    roots.incrementAndGet();
                    return "html";
                  },
                  null,
                  r -> Props.empty(),
                  false,
                  false),
              codec,
              new PropsResolver(
                  codec,
                  executor,
                  Duration.ofSeconds(1),
                  2,
                  Clock.fixed(Instant.ofEpochMilli(1_700_000_000_999L), ZoneOffset.UTC)));
      var context =
          new InertiaContext(
              new InertiaRequest("GET", URI.create("https://app.test/ttl"), Map.of()),
              session,
              codec);
      var error =
          assertThrows(
              ExecutionException.class,
              () ->
                  renderer
                      .render(
                          context,
                          new InertiaResponse(
                              "Home",
                              Props.builder()
                                  .put(
                                      "catalog",
                                      Prop.value(7)
                                          .once()
                                          .until(Duration.ofSeconds(Long.MAX_VALUE)))
                                  .build()))
                      .toCompletableFuture()
                      .get());
      assertEquals(InertiaObserver.Reason.ERROR, Observations.failureReason(error));
      assertEquals(0, roots.get());
      var delivery = session.beginPageDelivery();
      assertEquals("keep", delivery.data().at("/inertia.flash_data/message").asText());
      session.abortPageDelivery(delivery);
    }
  }

  @Test
  void negativeTtlIsRejectedBeforeRequestWork() {
    assertThrows(IllegalArgumentException.class, () -> Prop.value(7).until(Duration.ofNanos(-1)));
  }
}
