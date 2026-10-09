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

  public MicrometerInertiaObserver(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry);
  }

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
