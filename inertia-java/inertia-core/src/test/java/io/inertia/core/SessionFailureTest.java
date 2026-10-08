package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SessionFailureTest {
  final PageCodec codec = new PageCodec();

  enum Operation {
    BEGIN,
    COMPLETE,
    ABORT,
    MERGE
  }

  static class FailingStore implements SessionStore {
    final MemorySessionStore data = new MemorySessionStore();
    final Set<Operation> failures = EnumSet.noneOf(Operation.class);
    final Map<Operation, Integer> calls = new EnumMap<>(Operation.class);

    void operation(Operation op) {
      calls.merge(op, 1, Integer::sum);
      if (failures.contains(op)) throw new IllegalStateException(op.name() + " failed");
    }

    public JsonNode get(String key) {
      return data.get(key);
    }

    public void put(String key, JsonNode value) {
      data.put(key, value);
    }

    public JsonNode pull(String key) {
      return data.pull(key);
    }

    public Delivery beginPageDelivery() {
      operation(Operation.BEGIN);
      return data.beginPageDelivery();
    }

    public void completePageDelivery(Delivery token) {
      operation(Operation.COMPLETE);
      data.completePageDelivery(token);
    }

    public void abortPageDelivery(Delivery token) {
      operation(Operation.ABORT);
      data.abortPageDelivery(token);
    }

    public void merge(ObjectNode pending) {
      operation(Operation.MERGE);
      data.merge(pending);
    }
  }

  InertiaContext context(SessionStore store) {
    return new InertiaContext(
        new InertiaRequest("GET", URI.create("https://app.test/"), Map.of("x-inertia", "true")),
        store,
        codec);
  }

  ResponseRenderer renderer(ExecutorService executor) {
    return new ResponseRenderer(
        InertiaConfig.basic("v1", Set.of("Home")),
        codec,
        new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
  }

  @Test
  void beginFailureSkipsCallbacksAndClosesContext() {
    var store = new FailingStore();
    store.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
    store.failures.add(Operation.BEGIN);
    var context = context(store);
    var callbacks = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      assertThrows(
          CompletionException.class,
          () ->
              renderer(executor)
                  .render(
                      context,
                      new InertiaResponse(
                          "Home",
                          Props.builder()
                              .put("callback", Prop.lazy(callbacks::incrementAndGet))
                              .build()))
                  .toCompletableFuture()
                  .join());
      assertEquals(0, callbacks.get());
      assertEquals("keep", store.get(InertiaContext.FLASH).path("toast").asText());
      assertThrows(IllegalStateException.class, () -> context.flash("late", 1));
      assertEquals(1, store.calls.get(Operation.BEGIN));
    }
  }

  @Test
  void completionFailureFailsResponseAndAttemptsOneRestore() throws Exception {
    var store = new FailingStore();
    store.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
    store.failures.add(Operation.COMPLETE);
    var context = context(store);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var failed =
          renderer(executor)
              .render(context, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture();
      var error = assertThrows(ExecutionException.class, failed::get);
      assertEquals("COMPLETE failed", error.getCause().getMessage());
      assertEquals("keep", store.get(InertiaContext.FLASH).path("toast").asText());
      context.abort();
      assertEquals(1, store.calls.get(Operation.COMPLETE));
      assertEquals(1, store.calls.get(Operation.ABORT));
      assertThrows(IllegalStateException.class, () -> context.flash("late", 1));
    }
  }

  @Test
  void restoreFailurePreservesOriginalCauseAndDoesNotRetry() throws Exception {
    var store = new FailingStore();
    store.failures.add(Operation.ABORT);
    var context = context(store);
    var original = new IllegalArgumentException("original failure");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var failed =
          renderer(executor)
              .render(
                  context,
                  new InertiaResponse(
                      "Home",
                      Props.builder()
                          .put(
                              "failure",
                              Prop.lazy(
                                  () -> {
                                    throw original;
                                  }))
                          .build()))
              .toCompletableFuture();
      var error = assertThrows(ExecutionException.class, failed::get).getCause();
      assertInstanceOf(PropResolutionException.class, error);
      Throwable root = error;
      while (root.getCause() != null && root.getCause() != root) root = root.getCause();
      assertSame(original, root);
      assertEquals(1, error.getSuppressed().length);
      assertEquals("ABORT failed", error.getSuppressed()[0].getMessage());
      context.abort();
      assertEquals(1, store.calls.get(Operation.ABORT));
      assertThrows(IllegalStateException.class, () -> context.flash("late", 1));
    }
  }

  @Test
  void redirectWriteFailureNeverCommitsOrReplays() {
    var store = new FailingStore();
    store.failures.add(Operation.MERGE);
    var context = context(store);
    context.flash("toast", "pending");
    assertThrows(IllegalStateException.class, context::commitRedirect);
    assertNull(store.get(InertiaContext.FLASH));
    assertThrows(IllegalStateException.class, context::commitRedirect);
    assertThrows(IllegalStateException.class, () -> context.flash("late", 1));
    assertEquals(1, store.calls.get(Operation.MERGE));
  }

  @Test
  void memoryMergeAndRestoreAreAtomicOnInvalidPayload() {
    var store = new MemorySessionStore();
    store.put(InertiaContext.FLASH, codec.value(Map.of("old", "keep")));
    var malformed = codec.object();
    malformed.set(InertiaContext.FLASH, codec.value(Map.of("new", "must not partially write")));
    malformed.put(InertiaContext.ERRORS, "invalid");
    assertThrows(IllegalArgumentException.class, () -> store.merge(malformed));
    assertFalse(store.get(InertiaContext.FLASH).has("new"));
    assertNull(store.get(InertiaContext.ERRORS));
    var token = store.beginPageDelivery();
    store.put(InertiaContext.FLASH, codec.value(Map.of("new", "keep too")));
    store.put(InertiaContext.ERRORS, codec.value("invalid"));
    assertThrows(IllegalArgumentException.class, () -> store.abortPageDelivery(token));
    assertEquals("keep too", store.get(InertiaContext.FLASH).path("new").asText());
    store.pull(
        InertiaContext.ERRORS); // Explicit repair, not an automatic retry of an unknown outcome.
    store.abortPageDelivery(token);
    assertEquals("keep", store.get(InertiaContext.FLASH).path("old").asText());
    assertEquals("keep too", store.get(InertiaContext.FLASH).path("new").asText());
  }

  @Test
  void deliverySnapshotIsDefensive() {
    var source = codec.object().put("value", "original");
    var token = new SessionStore.Delivery(UUID.randomUUID(), source);
    source.put("value", "changed");
    token.data().put("value", "changed again");
    assertEquals("original", token.data().path("value").asText());
  }
}
