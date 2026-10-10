package io.inertia.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoBenchmarkTest {
  @Test
  void localLoadFixturesRequireExplicitOptIn() {
    var runner = new ApplicationContextRunner().withUserConfiguration(DemoBenchmark.class);
    runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DemoBenchmark.class));
    try (var pool =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2))) {
      runner
          .withPropertyValues("inertia.benchmark-enabled=true")
          .withBean("inertiaPropsExecutor", ExecutorService.class, () -> pool)
          .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DemoBenchmark.class));
    }
  }
}
