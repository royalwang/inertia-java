package io.inertia.boot;

import io.inertia.core.InertiaObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

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
  @Bean
  @ConditionalOnSingleCandidate(MeterRegistry.class)
  @ConditionalOnMissingBean(InertiaObserver.class)
  public InertiaObserver inertiaMetricsObserver(MeterRegistry registry) {
    return new MicrometerInertiaObserver(registry);
  }
}
