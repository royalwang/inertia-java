package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class AdvancedPropsTest {
  PageCodec codec = new PageCodec();
  ExecutorService executor;
  PropsResolver resolver;

  @BeforeEach
  void setup() {
    executor = Executors.newFixedThreadPool(4);
    resolver =
        new PropsResolver(
            codec,
            executor,
            Duration.ofSeconds(1),
            4,
            Clock.fixed(Instant.ofEpochMilli(1_000_123), ZoneOffset.UTC));
  }

  @AfterEach
  void stop() {
    executor.shutdownNow();
  }

  InertiaRequest request(Map<String, String> headers) {
    return new InertiaRequest("GET", URI.create("https://app.test/users"), headers);
  }

  PropsResolver.Resolved resolve(Map<String, String> headers, Props props) throws Exception {
    return resolver
        .resolve(request(headers), "Home", Props.empty(), props)
        .toCompletableFuture()
        .get();
  }

  @Test
  void appendPrependDeepMatchAndResetMetadata() throws Exception {
    var props =
        Props.builder()
            .put("posts", Prop.value(List.of(Map.of("id", 1))).merge().matchOn("id"))
            .put("older", Prop.value(List.of(1)).prepend())
            .put("tree", Prop.value(Map.of("a", 1)).deepMerge())
            .put(
                "nested",
                Prop.value(Map.of("data", List.of(1))).appendAt("data").matchOn("data.id"))
            .build();
    var result = resolve(Map.of(), props).metadata();
    assertEquals(codec.value(List.of("posts", "nested.data")), result.path("mergeProps"));
    assertEquals(codec.value(List.of("older")), result.path("prependProps"));
    assertEquals(codec.value(List.of("tree")), result.path("deepMergeProps"));
    assertEquals(codec.value(List.of("posts.id", "nested.data.id")), result.path("matchPropsOn"));
    var reset = resolve(Map.of("x-inertia-reset", "posts,nested"), props).metadata();
    assertFalse(reset.has("mergeProps"));
    assertFalse(reset.has("matchPropsOn"));
  }

  @Test
  void onceSkippingExplicitPartialFreshAndClockTtl() throws Exception {
    var calls = new AtomicInteger();
    var once =
        Prop.lazy(() -> calls.incrementAndGet())
            .onceAs("plans-cache")
            .until(Duration.ofSeconds(60));
    var props = Props.builder().put("plans", once).build();
    var first = resolve(Map.of("x-inertia-except-once-props", "plans-cache"), props);
    assertEquals(1, calls.get());
    assertEquals(1060000, first.metadata().at("/onceProps/plans-cache/expiresAt").asLong());
    var loaded =
        resolve(Map.of("x-inertia", "true", "x-inertia-except-once-props", "plans-cache"), props);
    assertFalse(loaded.props().has("plans"));
    assertEquals(1, calls.get());
    resolve(
        Map.of(
            "x-inertia",
            "true",
            "x-inertia-except-once-props",
            "plans-cache",
            "x-inertia-partial-component",
            "Home",
            "x-inertia-partial-data",
            "plans"),
        props);
    assertEquals(2, calls.get());
    resolve(
        Map.of("x-inertia", "true", "x-inertia-except-once-props", "plans-cache"),
        Props.builder().put("plans", once.fresh()).build());
    assertEquals(3, calls.get());
    var unlimited = resolve(Map.of(), Props.builder().put("x", Prop.value(1).once()).build());
    assertTrue(unlimited.metadata().at("/onceProps/x/expiresAt").isNull());
  }

  @Test
  void deferredOnceMergeCompositionPreservesOrdering() throws Exception {
    var props =
        Props.builder()
            .put("feed", Prop.defer(() -> 1).group("feed-group").merge().onceAs("feed-key"))
            .put("other", Prop.defer(() -> 2).group("feed-group"))
            .build();
    var initial = resolve(Map.of(), props).metadata();
    assertEquals(codec.value(List.of("feed", "other")), initial.at("/deferredProps/feed-group"));
    assertEquals("feed", initial.at("/mergeProps/0").asText());
    var loaded =
        resolve(Map.of("x-inertia", "true", "x-inertia-except-once-props", "feed-key"), props)
            .metadata();
    assertEquals(codec.value(List.of("other")), loaded.at("/deferredProps/feed-group"));
  }

  @Test
  void scrollDirectionResetAndLazyMetadata() throws Exception {
    var props =
        Props.builder()
            .put(
                "users",
                Prop.scrollWith(() -> new ScrollPage(List.of(Map.of("id", 1)), null, 2, 1))
                    .matchOn("data.id"))
            .build();
    var result = resolve(Map.of("x-inertia-infinite-scroll-merge-intent", "prepend"), props);
    assertEquals(1, result.props().at("/users/data/0/id").asInt());
    assertEquals("users.data", result.metadata().at("/prependProps/0").asText());
    assertTrue(result.metadata().at("/scrollProps/users/previousPage").isNull());
    assertEquals(2, result.metadata().at("/scrollProps/users/nextPage").asInt());
    var reset = resolve(Map.of("x-inertia-reset", "users"), props).metadata();
    assertFalse(reset.has("mergeProps"));
    assertTrue(reset.at("/scrollProps/users/reset").asBoolean());
  }
}
