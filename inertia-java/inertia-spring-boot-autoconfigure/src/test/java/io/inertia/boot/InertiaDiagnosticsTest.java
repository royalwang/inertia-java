package io.inertia.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.inertia.core.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InertiaDiagnosticsTest {
  final PageCodec codec = new PageCodec();
  final InertiaRequest request =
      new InertiaRequest("GET", URI.create("https://app.test/?private=secret"), Map.of());

  @ParameterizedTest
  @ValueSource(strings = {"timeout", "overloaded", "business"})
  void failedPropsAreDistinguishedFromSsrFallbackAndFlashCanRecover(String scenario)
      throws Exception {
    var registry = new SimpleMeterRegistry();
    try (var pool = Executors.newSingleThreadExecutor()) {
      var events = new CopyOnWriteArrayList<InertiaObserver.Event>();
      var observer = InertiaObserver.combine(new MicrometerInertiaObserver(registry), events::add);
      var reject = new AtomicBoolean(scenario.equals("overloaded"));
      Executor executor =
          command -> {
            if (reject.get()) throw new RejectedExecutionException("private-executor-detail");
            pool.execute(command);
          };
      var source = new CompletableFuture<String>();
      Prop value =
          switch (scenario) {
            case "timeout" -> Prop.async(() -> source);
            case "overloaded" -> Prop.lazy(() -> "value");
            default ->
                Prop.lazy(
                    () -> {
                      throw new IllegalStateException("private-provider-detail");
                    });
          };
      String reason = scenario.equals("business") ? "error" : scenario;
      var resolver =
          new PropsResolver(
              codec, executor, Duration.ofMillis(200), 1, Clock.systemUTC(), observer);
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Home")), codec, resolver, observer, "renderer");
      var session = new MemorySessionStore();
      session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "preserved")));
      var failed =
          renderer
              .render(
                  new InertiaContext(request, session, codec),
                  new InertiaResponse("Home", Props.builder().put("value", value).build()))
              .toCompletableFuture();
      assertThrows(ExecutionException.class, () -> failed.get(3, TimeUnit.SECONDS));
      awaitTimer(registry, "inertia.props", reason);
      awaitTimer(registry, "inertia.render", reason);
      assertThat(registry.find("inertia.ssr").timer()).isNull();
      assertThat(session.get(InertiaContext.FLASH).get("toast").asText()).isEqualTo("preserved");
      assertThat(codec.value(events).toString())
          .doesNotContain("private-executor-detail", "private-provider-detail", "?private=secret");
      reject.set(false);
      var recovered =
          renderer
              .render(
                  new InertiaContext(request, session, codec),
                  new InertiaResponse(
                      "Home", Props.builder().put("value", Prop.lazy(() -> "recovered")).build()))
              .toCompletableFuture()
              .get(3, TimeUnit.SECONDS);
      assertThat(recovered.status()).isEqualTo(200);
      assertThat(session.get(InertiaContext.FLASH)).isNull();
      awaitTimer(registry, "inertia.render", "none");
      assertThat(registry.get("inertia.ssr").tag("reason", "disabled").timer().count())
          .isEqualTo(1);
    } finally {
      registry.close();
    }
  }

  @Test
  void sessionFailureHasItsOwnStageAndDoesNotBecomeAnSsrFallback() {
    var registry = new SimpleMeterRegistry();
    try {
      var observer = new MicrometerInertiaObserver(registry);
      var session = org.mockito.Mockito.spy(new MemorySessionStore());
      org.mockito.Mockito.doThrow(new IllegalStateException("private-session-detail"))
          .when(session)
          .merge(org.mockito.ArgumentMatchers.any());
      var failed = new InertiaContext(request, session, codec, observer);
      failed.flash("toast", "pending");
      assertThrows(IllegalStateException.class, failed::commitRedirect);
      assertThat(registry.get("inertia.session_merge").tag("reason", "error").timer().count())
          .isEqualTo(1);
      assertThat(registry.find("inertia.ssr").timer()).isNull();
      org.mockito.Mockito.doCallRealMethod()
          .when(session)
          .merge(org.mockito.ArgumentMatchers.any());
      var recovered = new InertiaContext(request, session, codec, observer);
      recovered.flash("toast", "restored");
      recovered.commitRedirect();
      assertThat(session.get(InertiaContext.FLASH).get("toast").asText()).isEqualTo("restored");
      assertThat(registry.get("inertia.session_merge").tag("reason", "none").timer().count())
          .isEqualTo(1);
    } finally {
      registry.close();
    }
  }

  private static void awaitTimer(SimpleMeterRegistry registry, String name, String reason)
      throws InterruptedException {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < end) {
      var timer = registry.find(name).tag("reason", reason).timer();
      if (timer != null && timer.count() == 1) return;
      Thread.sleep(1);
    }
    assertThat(registry.get(name).tag("reason", reason).timer().count()).isEqualTo(1);
  }
}
