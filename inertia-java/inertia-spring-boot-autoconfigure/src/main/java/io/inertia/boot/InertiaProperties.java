package io.inertia.boot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Validated {@code inertia.*} Servlet execution settings bound by Spring Boot.
 *
 * <p>Binding supplies the annotated defaults. Direct Java construction must supply all values;
 * response timeout must cover the prop timeout and maximum pool size must cover core size.
 *
 * @param propsTimeout total prop-resolution budget; binding default is 3 seconds
 * @param responseTimeout Servlet response budget; binding default is 5 seconds
 * @param propsConcurrency positive per-request provider concurrency; binding default is 8
 * @param executorCoreSize positive shared executor core size; binding default is 8
 * @param executorMaxSize shared executor maximum size, at least core size; binding default is 32
 * @param executorQueueCapacity positive shared executor queue capacity; binding default is 256
 * @param allErrors validation presentation override, or null to retain {@link
 *     io.inertia.core.InertiaConfig#allErrors()}
 * @param sessionNamespace session-key namespace matching {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}};
 *     binding default is {@code default}
 */
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
  /**
   * Validates the bound settings before any executor or MVC adapter is created.
   *
   * @throws IllegalArgumentException if budgets, pool limits, concurrency, or namespace are invalid
   */
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
