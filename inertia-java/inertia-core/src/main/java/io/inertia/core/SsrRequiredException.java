package io.inertia.core;

import java.util.Objects;

/**
 * Required HTML rendering could not use SSR. Adapters should return a safe service-unavailable
 * response.
 */
public final class SsrRequiredException extends RuntimeException {
  /**
   * Bounded SSR diagnostic category.
   *
   * @serial required-render failure reason
   */
  private final InertiaObserver.Reason reason;

  /**
   * Creates an unavailable-required-SSR failure without an underlying exception.
   *
   * @param reason non-null bounded diagnostic classification
   * @throws NullPointerException if reason is null
   */
  public SsrRequiredException(InertiaObserver.Reason reason) {
    this(reason, null);
  }

  /**
   * Creates an unavailable-required-SSR failure with an optional underlying cause.
   *
   * @param reason non-null bounded diagnostic classification
   * @param cause diagnostic cause, or null; do not expose its message in a client Page
   * @throws NullPointerException if reason is null
   */
  public SsrRequiredException(InertiaObserver.Reason reason, Throwable cause) {
    super("Required server rendering is unavailable", cause);
    this.reason = Objects.requireNonNull(reason);
  }

  /**
   * Returns the bounded SSR failure classification.
   *
   * @return reason supplied at construction
   */
  public InertiaObserver.Reason reason() {
    return reason;
  }
}
