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
  void bindsBudgetsAndBuildsBoundedExecutor() {
    runner
        .withPropertyValues(
            "inertia.props-timeout=500ms",
            "inertia.response-timeout=2s",
            "inertia.executor-core-size=2",
            "inertia.executor-max-size=4",
            "inertia.executor-queue-capacity=7",
            "inertia.props-concurrency=3")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(ResponseRenderer.class);
              var properties = context.getBean(InertiaProperties.class);
              assertThat(properties.propsTimeout().toMillis()).isEqualTo(500);
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
  void nonServletApplicationDoesNotCreateInertiaBeans() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(InertiaAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ResponseRenderer.class));
  }
}
