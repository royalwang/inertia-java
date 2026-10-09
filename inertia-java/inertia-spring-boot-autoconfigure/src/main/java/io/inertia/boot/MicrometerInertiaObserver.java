package io.inertia.boot;

import io.inertia.core.InertiaObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Timer count and duration, with exclusively bounded tags; no request or component identifiers. */
public final class MicrometerInertiaObserver implements InertiaObserver {
  private final MeterRegistry registry;

  /**
   * Creates an observer without taking ownership of the registry.
   *
   * @param registry application-owned non-null registry
   * @throws NullPointerException if the registry is null
   */
  public MicrometerInertiaObserver(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry);
  }

  /**
   * Records one operation timer sample using bounded event fields only.
   *
   * <p>Names use {@code inertia.} followed by the lowercase operation. Tags are outcome, reason,
   * response kind, and status; request URLs, prop names, and component names are never used as
   * tags.
   *
   * @param event validated operation event carrying elapsed nanoseconds
   */
  @Override
  public void observe(Event event) {
    Timer.builder("inertia." + token(event.operation()))
        .tags(
            "outcome",
            token(event.outcome()),
            "reason",
            token(event.reason()),
            "response",
            token(event.response()),
            "status",
            Integer.toString(event.status()))
        .register(registry)
        .record(event.elapsedNanos(), TimeUnit.NANOSECONDS);
  }

  private static String token(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
