package io.inertia.boot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Validated execution budgets and optional validation presentation override. */
@ConfigurationProperties("inertia")
public record InertiaProperties(
    @DefaultValue("3s") Duration propsTimeout,
    @DefaultValue("5s") Duration responseTimeout,
    @DefaultValue("8") int propsConcurrency,
    @DefaultValue("8") int executorCoreSize,
    @DefaultValue("32") int executorMaxSize,
    @DefaultValue("256") int executorQueueCapacity,
    Boolean allErrors,
    @DefaultValue("default") String sessionNamespace) {
  public InertiaProperties {
    io.inertia.core.SessionStore.requireNamespace(sessionNamespace);
    if (propsTimeout == null
        || propsTimeout.isNegative()
        || propsTimeout.isZero()
        || responseTimeout == null
        || responseTimeout.isNegative()
        || responseTimeout.isZero())
      throw new IllegalArgumentException("Inertia timeouts must be positive");
    if (propsConcurrency < 1
        || executorCoreSize < 1
        || executorMaxSize < executorCoreSize
        || executorQueueCapacity < 1)
      throw new IllegalArgumentException("Invalid Inertia executor limits");
    if (responseTimeout.compareTo(propsTimeout) < 0)
      throw new IllegalArgumentException("Response timeout must cover props timeout");
  }
}
