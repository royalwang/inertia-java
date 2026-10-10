package io.inertia.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.inertia.core.*;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.*;

class InertiaAutoConfigurationTest {
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(InertiaAutoConfiguration.class))
          .withBean(InertiaConfig.class, () -> InertiaConfig.basic("v1", Set.of("Home")));

  @Test
  void candidateDefaultsRemainStableWhenNoPropertiesAreSet() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          var properties = context.getBean(InertiaProperties.class);
          assertThat(properties.propsTimeout()).isEqualTo(java.time.Duration.ofSeconds(3));
          assertThat(properties.responseTimeout()).isEqualTo(java.time.Duration.ofSeconds(5));
          assertThat(properties.propsConcurrency()).isEqualTo(8);
          assertThat(properties.executorCoreSize()).isEqualTo(8);
          assertThat(properties.executorMaxSize()).isEqualTo(32);
          assertThat(properties.executorQueueCapacity()).isEqualTo(256);
          assertThat(properties.allErrors()).isNull();
          assertThat(properties.sessionNamespace()).isEqualTo("default");
          var executor = (ThreadPoolExecutor) context.getBean("inertiaPropsExecutor");
          assertThat(executor.getCorePoolSize()).isEqualTo(8);
          assertThat(executor.getMaximumPoolSize()).isEqualTo(32);
          assertThat(executor.getQueue().remainingCapacity()).isEqualTo(256);
        });
  }

  @Test
  void applicationSessionFactoryReplacesOnlyTheDefaultFactory() {
    io.inertia.spring.InertiaSessionStoreFactory selected = request -> new MemorySessionStore();
    runner
        .withBean(io.inertia.spring.InertiaSessionStoreFactory.class, () -> selected)
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(io.inertia.spring.InertiaSessionStoreFactory.class);
              assertThat(context.getBean(io.inertia.spring.InertiaSessionStoreFactory.class))
                  .isSameAs(selected);
              assertThat(context.getBean("inertiaMvcConfigurer"))
                  .isInstanceOf(io.inertia.spring.InertiaMvcConfigurer.class);
            });
  }

  @Test
  void bindsBudgetsAndBuildsBoundedExecutor() {
    runner
        .withPropertyValues(
            "inertia.props-timeout=500ms",
            "inertia.response-timeout=2s",
            "inertia.executor-core-size=2",
            "inertia.executor-max-size=4",
            "inertia.executor-queue-capacity=7",
            "inertia.props-concurrency=3",
            "inertia.session-namespace=portal")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(ResponseRenderer.class);
              var properties = context.getBean(InertiaProperties.class);
              assertThat(properties.propsTimeout().toMillis()).isEqualTo(500);
              assertThat(properties.sessionNamespace()).isEqualTo("portal");
              var executor = (ThreadPoolExecutor) context.getBean("inertiaPropsExecutor");
              assertThat(executor.getCorePoolSize()).isEqualTo(2);
              assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
              assertThat(executor.getQueue().remainingCapacity()).isEqualTo(7);
            });
  }

  @Test
  void customExecutorBacksOffAndOtherExecutorsDoNotCauseAmbiguity() {
    try (var custom = Executors.newSingleThreadExecutor();
        var unrelated = Executors.newSingleThreadExecutor()) {
      runner
          .withBean("inertiaPropsExecutor", ExecutorService.class, () -> custom)
          .withBean("businessExecutor", ExecutorService.class, () -> unrelated)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean("inertiaPropsExecutor")).isSameAs(custom);
                assertThat(context).hasSingleBean(PropsResolver.class);
              });
    }
  }

  @Test
  void invalidBudgetsFailAtStartup() {
    runner
        .withPropertyValues("inertia.props-timeout=0ms")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("inertia.executor-core-size=9", "inertia.executor-max-size=2")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("inertia.props-timeout=5s", "inertia.response-timeout=1s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void allErrorsPropertyOverridesConfigOnlyWhenExplicitlySet() {
    var configured =
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InertiaAutoConfiguration.class))
            .withBean(
                InertiaConfig.class,
                () -> InertiaConfig.basic("v1", Set.of("Home")).withAllErrors(true));
    configured.run(context -> assertRenderedErrors(context.getBean(ResponseRenderer.class), true));
    configured
        .withPropertyValues("inertia.all-errors=false")
        .run(context -> assertRenderedErrors(context.getBean(ResponseRenderer.class), false));
    runner
        .withPropertyValues("inertia.all-errors=true")
        .run(context -> assertRenderedErrors(context.getBean(ResponseRenderer.class), true));
  }

  private void assertRenderedErrors(ResponseRenderer renderer, boolean all) {
    var codec = new PageCodec();
    var context =
        new InertiaContext(
            new InertiaRequest(
                "GET",
                java.net.URI.create("https://app.test/"),
                java.util.Map.of("x-inertia", "true")),
            null,
            codec);
    context.withErrors(java.util.Map.of("name", java.util.List.of("Required", "Too short")));
    var outcome =
        renderer
            .render(context, new InertiaResponse("Home", Props.empty()))
            .toCompletableFuture()
            .join();
    assertThat(codec.read(outcome.body()).at("/props/errors/name").isArray()).isEqualTo(all);
  }

  @Test
  void invalidNamespaceFailsAtStartup() {
    runner
        .withPropertyValues("inertia.session-namespace=../portal")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("inertia.session-namespace=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void nonServletApplicationDoesNotCreateInertiaBeans() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(InertiaAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ResponseRenderer.class));
  }
}
