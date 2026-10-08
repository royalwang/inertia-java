package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class CoreContractTest {
  private final PageCodec codec = new PageCodec();
  private ExecutorService executor;
  private PropsResolver resolver;

  @BeforeEach
  void setup() {
    executor = Executors.newFixedThreadPool(8);
    resolver = new PropsResolver(codec, executor, Duration.ofSeconds(2), 8);
  }

  @AfterEach
  void stop() {
    executor.shutdownNow();
  }

  private InertiaRequest request(String method, Map<String, String> headers) {
    return new InertiaRequest(method, URI.create("https://app.test/users?page=2"), headers);
  }

  @Test
  void versionConflictsOnlyForInertiaGet() {
    var result =
        ProtocolPolicy.before(
                request("GET", Map.of("X-Inertia", "true", "X-Inertia-Version", "old")), "new")
            .orElseThrow();
    assertEquals(409, result.status());
    assertEquals("https://app.test/users?page=2", result.header("X-Inertia-Location"));
    assertNull(result.header("X-Inertia"));
    assertTrue(
        ProtocolPolicy.before(request("POST", Map.of("X-Inertia", "true")), "new").isEmpty());
    assertTrue(ProtocolPolicy.before(request("GET", Map.of()), "new").isEmpty());
  }

  @Test
  void redirectsFragmentsAndPrefetch() {
    for (String method : List.of("PUT", "PATCH", "DELETE"))
      assertEquals(
          303,
          ProtocolPolicy.after(
                  request(method, Map.of("x-inertia", "true")), ProtocolPolicy.redirect("/users"))
              .status());
    assertEquals(
        302,
        ProtocolPolicy.after(
                request("POST", Map.of("x-inertia", "true")), ProtocolPolicy.redirect("/users"))
            .status());
    var fragment =
        ProtocolPolicy.after(
            request("GET", Map.of("x-inertia", "true")), ProtocolPolicy.redirect("/users#top"));
    assertEquals(409, fragment.status());
    assertEquals("/users#top", fragment.header("x-inertia-redirect"));
    assertEquals(
        302,
        ProtocolPolicy.after(
                request("GET", Map.of("x-inertia", "true", "purpose", "PREFETCH")),
                ProtocolPolicy.redirect("/users#top"))
            .status());
    assertEquals("/", request("GET", Map.of("referer", "https://evil.test/")).safeBack());
    assertEquals("/edit", request("GET", Map.of("referer", "https://app.test/edit")).safeBack());
  }

  @Test
  void varyAndHeaderInjection() {
    var response = HttpOutcome.empty(200).withHeader("Vary", "Accept-Encoding").vary().vary();
    assertEquals(List.of("Accept-Encoding", "X-Inertia"), response.headers().get("vary"));
    assertThrows(
        IllegalArgumentException.class, () -> ProtocolPolicy.redirect("/x\r\nInjected: yes"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InertiaResponse("Home", Props.empty()).withHeader("content-type", "text/plain"));
  }

  @Test
  void htmlJsonRoundTripAndIntegers() {
    var node = codec.object();
    node.put("component", "Home");
    node.set(
        "props",
        codec.value(Map.of("attack", "</script><script>&\u2028\u2029", "id", Long.MAX_VALUE)));
    node.put("url", "/");
    node.put("version", "v1");
    Page page = new Page(node);
    String escaped = codec.htmlJson(page);
    assertFalse(escaped.contains("</script>"));
    assertFalse(escaped.contains("\u2028"));
    assertEquals(node, codec.read(escaped));
    assertEquals(
        "9223372036854775807",
        codec.bigIntegers(node).path("props").path("id").path("$bigint").asText());
    node.put("component", "changed");
    assertEquals("Home", page.component());
  }

  @Test
  void partialPlansBeforeQueriesAndAlwaysSurvivesExcept() throws Exception {
    var calls = new AtomicInteger();
    var props =
        Props.builder()
            .put("auth.user", Map.of("name", "Ada", "secret", "hidden"))
            .put(
                "expensive",
                Prop.lazy(
                    () -> {
                      calls.incrementAndGet();
                      return 1;
                    }))
            .put("always", Prop.always(7))
            .put("optional", Prop.optional(() -> 9))
            .build();
    var req =
        request(
            "GET",
            Map.of(
                "x-inertia",
                "true",
                "x-inertia-partial-component",
                "Home",
                "x-inertia-partial-data",
                "auth.user.name",
                "x-inertia-partial-except",
                "always"));
    var result = resolver.resolve(req, "Home", Props.empty(), props).toCompletableFuture().get();
    assertEquals("Ada", result.props().at("/auth/user/name").asText());
    assertFalse(result.props().at("/auth/user").has("secret"));
    assertEquals(7, result.props().path("always").asInt());
    assertEquals(0, calls.get());
    assertFalse(result.props().has("optional"));
  }

  @Test
  void differentComponentIsFullAndDeferredDoesNotRunInitially() throws Exception {
    var calls = new AtomicInteger();
    var props =
        Props.builder()
            .put(
                "stats",
                Prop.defer(
                        () -> {
                          calls.incrementAndGet();
                          return 3;
                        })
                    .group("dashboard"))
            .put("name", "Ada")
            .build();
    var full =
        resolver
            .resolve(
                request(
                    "GET",
                    Map.of(
                        "x-inertia-partial-component",
                        "Other",
                        "x-inertia-partial-data",
                        "nothing")),
                "Home",
                Props.empty(),
                props)
            .toCompletableFuture()
            .get();
    assertEquals("Ada", full.props().path("name").asText());
    assertEquals(0, calls.get());
    assertEquals("stats", full.metadata().at("/deferredProps/dashboard/0").asText());
    var partial =
        resolver
            .resolve(
                request(
                    "GET",
                    Map.of(
                        "x-inertia-partial-component", "Home", "x-inertia-partial-data", "stats")),
                "Home",
                Props.empty(),
                props)
            .toCompletableFuture()
            .get();
    assertEquals(3, partial.props().path("stats").asInt());
    assertEquals(1, calls.get());
    assertFalse(partial.metadata().has("deferredProps"));
  }

  @Test
  void computedObjectIsNotFilteredAndNestedPropsAreFiltered() throws Exception {
    var props =
        Props.builder()
            .put("computed", Prop.lazy(() -> Map.of("a", 1, "b", 2)))
            .put("nested", Props.builder().put("a", 1).put("b", 2).build())
            .build();
    var r =
        resolver
            .resolve(
                request(
                    "GET",
                    Map.of(
                        "x-inertia-partial-component",
                        "Home",
                        "x-inertia-partial-data",
                        "computed.a,nested.a")),
                "Home",
                Props.empty(),
                props)
            .toCompletableFuture()
            .get();
    assertEquals(2, r.props().at("/computed/b").asInt());
    assertFalse(r.props().path("nested").has("b"));
  }

  @Test
  void siblingSuppliersActuallyOverlap() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    Prop.Task task =
        () -> {
          entered.countDown();
          if (!release.await(1, TimeUnit.SECONDS))
            throw new IllegalStateException("Not concurrent");
          return 1;
        };
    var result =
        resolver
            .resolve(
                request("GET", Map.of()),
                "Home",
                Props.empty(),
                Props.builder().put("a", Prop.lazy(task)).put("b", Prop.lazy(task)).build())
            .toCompletableFuture();
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    release.countDown();
    assertEquals(1, result.get().props().path("a").asInt());
  }

  @Test
  void failuresAreNotSsrFallbackAndRescueIsExplicit() throws Exception {
    var props =
        Props.builder()
            .put(
                "bad",
                Prop.lazy(
                    () -> {
                      throw new IllegalStateException("failure");
                    }))
            .build();
    assertThrows(
        ExecutionException.class,
        () ->
            resolver
                .resolve(request("GET", Map.of()), "Home", Props.empty(), props)
                .toCompletableFuture()
                .get());
    var rescued =
        Props.builder()
            .put(
                "bad",
                Prop.defer(
                        () -> {
                          throw new IllegalStateException();
                        })
                    .rescue())
            .build();
    var result =
        resolver
            .resolve(
                request(
                    "GET",
                    Map.of("x-inertia-partial-component", "Home", "x-inertia-partial-data", "bad")),
                "Home",
                Props.empty(),
                rescued)
            .toCompletableFuture()
            .get();
    assertFalse(result.props().has("bad"));
    assertEquals("bad", result.metadata().at("/rescuedProps/0").asText());
  }

  @Test
  void deadlineBoundsAsynchronousSupplier() {
    var shortResolver = new PropsResolver(codec, executor, Duration.ofMillis(30), 8);
    var pending = new CompletableFuture<Object>();
    var result =
        shortResolver
            .resolve(
                request("GET", Map.of()),
                "Home",
                Props.empty(),
                Props.builder().put("slow", Prop.async(() -> pending)).build())
            .toCompletableFuture();
    assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
  }

  @Test
  void conflictingPathsFailBeforeSupplierExecution() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Props.builder().put("a", Prop.lazy(() -> 1)).put("a.b", 2).build());
    assertThrows(IllegalArgumentException.class, () -> Props.builder().put("a..b", 1));
  }

  @Test
  void renderingPreservesStatusHeadersAndChoosesJsonOrDocument() throws Exception {
    var config = InertiaConfig.basic("v1", Set.of("Home"));
    var renderer = new ResponseRenderer(config, codec, resolver);
    var html =
        renderer
            .render(
                request("GET", Map.of()),
                new InertiaResponse("Home", Props.builder().put("name", "Ada").build())
                    .status(404)
                    .withHeader("Cache-Control", "no-store"))
            .toCompletableFuture()
            .get();
    assertEquals(404, html.status());
    assertTrue(html.body().contains("data-page=\"app\""));
    assertNull(html.header("x-inertia"));
    assertEquals("no-store", html.header("cache-control"));
    var response = new InertiaResponse("Home", Props.empty());
    var json =
        renderer
            .render(request("GET", Map.of("x-inertia", "true")), response)
            .toCompletableFuture()
            .get();
    assertEquals("true", json.header("x-inertia"));
    assertEquals("Home", codec.read(json.body()).path("component").asText());
    assertThrows(
        IllegalStateException.class, () -> renderer.render(request("GET", Map.of()), response));
  }
}
