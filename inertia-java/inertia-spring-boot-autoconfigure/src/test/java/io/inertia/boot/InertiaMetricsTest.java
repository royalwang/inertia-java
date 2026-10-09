package io.inertia.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.inertia.core.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.*;

class InertiaMetricsTest {
  final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  InertiaAutoConfiguration.class, InertiaMetricsAutoConfiguration.class))
          .withBean(InertiaConfig.class, () -> InertiaConfig.basic("v1", Set.of("Home")));

  @Test
  void registryWiresPropsRendererAndSessionEventsWithBoundedTags() {
    runner
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(InertiaObserver.class);
              var registry = context.getBean(SimpleMeterRegistry.class);
              var renderer = context.getBean(ResponseRenderer.class);
              assertThat(renderer.observer()).isInstanceOf(MicrometerInertiaObserver.class);
              render(renderer);
              var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
              while ((registry.getMeters().size() < 5
                      || registry.getMeters().stream()
                          .anyMatch(
                              meter -> ((io.micrometer.core.instrument.Timer) meter).count() == 0))
                  && System.nanoTime() < deadline)
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
              assertThat(registry.get("inertia.props").timer().count()).isEqualTo(1);
              assertThat(registry.get("inertia.ssr").tag("reason", "disabled").timer().count())
                  .isEqualTo(1);
              assertThat(registry.get("inertia.session_begin").timer().count()).isEqualTo(1);
              assertThat(registry.get("inertia.session_complete").timer().count()).isEqualTo(1);
              assertThat(
                      registry
                          .get("inertia.render")
                          .tag("response", "html")
                          .tag("status", "200")
                          .timer()
                          .count())
                  .isEqualTo(1);
              var observer = context.getBean(InertiaObserver.class);
              for (int i = 0; i < 200; i++)
                observer.observe(
                    new InertiaObserver.Event(
                        InertiaObserver.Operation.RENDER,
                        InertiaObserver.Outcome.SUCCESS,
                        InertiaObserver.Reason.NONE,
                        7,
                        200,
                        InertiaObserver.ResponseKind.HTML,
                        "id" + i,
                        "component" + i,
                        "endpoint" + i));
              assertThat(registry.getMeters()).hasSize(5);
              assertThat(registry.get("inertia.render").timer().count()).isEqualTo(201);
              assertThat(registry.get("inertia.render").timer().totalTime(TimeUnit.NANOSECONDS))
                  .isGreaterThanOrEqualTo(1400);
              assertThat(registry.getMeters())
                  .allSatisfy(
                      meter ->
                          assertThat(
                                  meter.getId().getTags().stream()
                                      .map(tag -> tag.getKey())
                                      .toList())
                              .containsExactlyInAnyOrder(
                                  "outcome", "reason", "response", "status"));
              observer.observe(
                  new InertiaObserver.Event(
                      InertiaObserver.Operation.PROP_OVERRIDE,
                      InertiaObserver.Outcome.SUCCESS,
                      InertiaObserver.Reason.ERRORS_OVERRIDE,
                      0,
                      0,
                      InertiaObserver.ResponseKind.NONE,
                      "private-id",
                      "Home",
                      "renderer"));
              var override =
                  registry.get("inertia.prop_override").tag("reason", "errors_override").timer();
              assertThat(override.count()).isEqualTo(1);
              assertThat(override.totalTime(TimeUnit.NANOSECONDS)).isZero();
              assertThat(override.getId().getTags().stream().map(tag -> tag.getKey()).toList())
                  .containsExactlyInAnyOrder("outcome", "reason", "response", "status");
            });
  }

  @Test
  void noRegistryOrMissingOptionalLibraryKeepsNoop() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed().doesNotHaveBean(InertiaObserver.class);
          assertThat(context.getBean(ResponseRenderer.class).observer())
              .isSameAs(InertiaObserver.NOOP);
          render(context.getBean(ResponseRenderer.class));
        });
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(InertiaObserver.class);
              render(context.getBean(ResponseRenderer.class));
            });
  }

  @Test
  void customObserverBacksOffMetricsAndReceivesConfiguredEndpoint() {
    var events = new CopyOnWriteArrayList<InertiaObserver.Event>();
    var delivered = new CountDownLatch(5);
    InertiaObserver observer =
        event -> {
          events.add(event);
          delivered.countDown();
        };
    runner
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .withBean(InertiaObserver.class, () -> observer)
        .withPropertyValues("inertia.ssr-endpoint-id=primary")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(InertiaObserver.class);
              assertThat(context.getBean(ResponseRenderer.class).observer()).isSameAs(observer);
              render(context.getBean(ResponseRenderer.class));
              assertThat(delivered.await(2, TimeUnit.SECONDS)).isTrue();
              assertThat(
                      events.stream()
                          .filter(e -> e.operation() == InertiaObserver.Operation.SSR)
                          .findFirst()
                          .orElseThrow()
                          .endpointId())
                  .isEqualTo("primary");
              assertThat(context.getBean(SimpleMeterRegistry.class).getMeters()).isEmpty();
            });
  }

  @Test
  void actuatorRegistryIsAvailableBeforeInertiaObserverConditions() {
    runner
        .withConfiguration(
            AutoConfigurations.of(
                MetricsAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class,
                SimpleMetricsExportAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(InertiaObserver.class)
                  .hasSingleBean(MeterRegistry.class);
              render(context.getBean(ResponseRenderer.class));
              awaitProps(context.getBean(MeterRegistry.class));
              assertThat(context.getBean(MeterRegistry.class).find("inertia.props").timer())
                  .isNotNull();
            });
  }

  @Test
  void multipleRegistriesRequirePrimaryAndUnsafeEndpointFailsStartup() {
    runner
        .withBean("firstRegistry", SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .withBean("secondRegistry", SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(InertiaObserver.class);
              assertThat(context.getBean(ResponseRenderer.class).observer())
                  .isSameAs(InertiaObserver.NOOP);
            });
    runner
        .withBean(
            "firstRegistry",
            SimpleMeterRegistry.class,
            SimpleMeterRegistry::new,
            definition -> definition.setPrimary(true))
        .withBean("secondRegistry", SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(InertiaObserver.class);
              render(context.getBean(ResponseRenderer.class));
              var selected = context.getBean("firstRegistry", SimpleMeterRegistry.class);
              awaitProps(selected);
              assertThat(selected.get("inertia.props").timer().count()).isEqualTo(1);
              assertThat(context.getBean("secondRegistry", SimpleMeterRegistry.class).getMeters())
                  .isEmpty();
            });
    runner
        .withPropertyValues("inertia.ssr-endpoint-id=https://secret.test/render")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void nonServletRegistryDoesNotEnableInertiaMetrics() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(InertiaMetricsAutoConfiguration.class))
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InertiaObserver.class));
  }

  private void render(ResponseRenderer renderer) {
    var codec = new PageCodec();
    var request =
        new InertiaRequest(
            "GET", URI.create("https://app.test/?secret=value"), Map.of("Cookie", "private"));
    assertThat(
            renderer
                .render(
                    new InertiaContext(request, new MemorySessionStore(), codec),
                    new InertiaResponse("Home", Props.empty()))
                .toCompletableFuture()
                .join()
                .status())
        .isEqualTo(200);
  }

  private void awaitProps(MeterRegistry registry) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while ((registry.find("inertia.props").timer() == null
            || registry.find("inertia.props").timer().count() == 0)
        && System.nanoTime() < deadline)
      java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
    assertThat(registry.find("inertia.props").timer()).isNotNull();
  }
}
