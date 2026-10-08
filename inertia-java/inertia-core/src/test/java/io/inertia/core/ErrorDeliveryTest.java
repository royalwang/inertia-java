package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ErrorDeliveryTest {
  final PageCodec codec = new PageCodec();
  final MemorySessionStore session = new MemorySessionStore();

  InertiaContext context(Map<String, String> headers) {
    return new InertiaContext(
        new InertiaRequest("GET", URI.create("https://app.test/"), headers), session, codec);
  }

  @Test
  void allMessagesSurviveRedirectMergeAndDefaultBagScopesPartialDelivery() throws Exception {
    var first = context(Map.of());
    first.withErrors(Map.of("name", List.of("Required", "Too short")));
    first.withErrors(Map.of("email", "Invalid"));
    first.withErrors("login", Map.of("password", "Wrong"));
    first.commitRedirect();
    var second = context(Map.of());
    second.withErrors(Map.of("name", "Use letters"));
    second.commitRedirect();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Home")).withAllErrors(true),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var request =
          context(
              Map.of(
                  "x-inertia",
                  "true",
                  "x-inertia-error-bag",
                  "signup",
                  "x-inertia-partial-component",
                  "Home",
                  "x-inertia-partial-data",
                  "other"));
      var json =
          codec.read(
              renderer
                  .render(request, new InertiaResponse("Home", Props.empty()))
                  .toCompletableFuture()
                  .get()
                  .body());
      assertEquals(
          codec.value(List.of("Required", "Too short", "Use letters")),
          json.at("/props/errors/signup/name"));
      assertEquals(codec.value(List.of("Invalid")), json.at("/props/errors/signup/email"));
      assertFalse(json.at("/props/errors").has("login"));
      var again =
          codec.read(
              renderer
                  .render(
                      context(Map.of("x-inertia", "true")),
                      new InertiaResponse("Home", Props.empty()))
                  .toCompletableFuture()
                  .get()
                  .body());
      assertTrue(again.at("/props/errors").isEmpty());
    }
  }

  @Test
  void failedPageRestoresErrorsAndMergesConcurrentMessages() throws Exception {
    var first = context(Map.of());
    first.withErrors(Map.of("name", List.of("Original"))).commitRedirect();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Home")).withAllErrors(true),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(2), 2));
      var failed =
          renderer
              .render(
                  context(Map.of("x-inertia", "true")),
                  new InertiaResponse(
                      "Home",
                      Props.builder()
                          .put(
                              "fails",
                              Prop.lazy(
                                  () -> {
                                    entered.countDown();
                                    release.await();
                                    throw new IllegalStateException("Fail");
                                  }))
                          .build()))
              .toCompletableFuture();
      assertTrue(entered.await(1, TimeUnit.SECONDS));
      context(Map.of()).withErrors(Map.of("name", List.of("Concurrent"))).commitRedirect();
      release.countDown();
      assertThrows(ExecutionException.class, failed::get);
      var json =
          codec.read(
              renderer
                  .render(
                      context(Map.of("x-inertia", "true")),
                      new InertiaResponse("Home", Props.empty()))
                  .toCompletableFuture()
                  .get()
                  .body());
      assertEquals(codec.value(List.of("Original", "Concurrent")), json.at("/props/errors/name"));
    } finally {
      release.countDown();
    }
  }

  @Test
  void legacySingleMessagesAreReadableAndResponseFlashOverridesRequestAndStoredFlash()
      throws Exception {
    session.put(InertiaContext.ERRORS, codec.value(Map.of("default", Map.of("name", "Legacy"))));
    session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "Old", "other", "Keep")));
    var request = context(Map.of("x-inertia", "true"));
    request.flash("toast", "Request");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Home")),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var json =
          codec.read(
              renderer
                  .render(
                      request,
                      new InertiaResponse("Home", Props.empty()).flash("toast", "Response"))
                  .toCompletableFuture()
                  .get()
                  .body());
      assertEquals("Legacy", json.at("/props/errors/name").asText());
      assertEquals("Response", json.at("/flash/toast").asText());
      assertEquals("Keep", json.at("/flash/other").asText());
    }
  }

  @Test
  void invalidRawValuesAreRejectedAndMessagesAreImmutable() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ValidationErrors.from(Map.of("password", new Object())));
    var messages = new ArrayList<>(List.of("Required"));
    var errors = ValidationErrors.from(Map.of("name", messages));
    messages.add("Mutated");
    assertEquals(List.of("Required"), errors.messages().get("name"));
    assertThrows(
        UnsupportedOperationException.class, () -> errors.messages().get("name").add("Other"));
  }
}
