package io.inertia.boot;

import io.inertia.core.InertiaObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

/**
 * Optional Servlet metrics wiring, ordered before the main Inertia configuration.
 *
 * <p>A single candidate {@link MeterRegistry} is required. An application-supplied {@link
 * InertiaObserver} disables this default observer; no metrics registry is created here.
 */
@AutoConfiguration(
    before = InertiaAutoConfiguration.class,
    afterName = {
      "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(MeterRegistry.class)
public class InertiaMetricsAutoConfiguration {
  /** Creates the configuration instance managed by Spring Boot. */
  public InertiaMetricsAutoConfiguration() {}

  /**
   * Creates the bounded-tag timer observer for the selected registry.
   *
   * @param registry application-owned metrics registry
   * @return observer recording Inertia operation counts and durations
   */
  @Bean
  @ConditionalOnSingleCandidate(MeterRegistry.class)
  @ConditionalOnMissingBean(InertiaObserver.class)
  public InertiaObserver inertiaMetricsObserver(MeterRegistry registry) {
    return new MicrometerInertiaObserver(registry);
  }
}
