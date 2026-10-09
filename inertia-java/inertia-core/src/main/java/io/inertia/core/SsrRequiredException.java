package io.inertia.core;

import java.util.Objects;

/**
 * Required HTML rendering could not use SSR. Adapters should return a safe service-unavailable
 * response.
 */
public final class SsrRequiredException extends RuntimeException {
  private final InertiaObserver.Reason reason;

  public SsrRequiredException(InertiaObserver.Reason reason) {
    this(reason, null);
  }

  public SsrRequiredException(InertiaObserver.Reason reason, Throwable cause) {
    super("Required server rendering is unavailable", cause);
    this.reason = Objects.requireNonNull(reason);
  }

  public InertiaObserver.Reason reason() {
    return reason;
  }
}
