package io.inertia.redis;

/** Bounded failure classification; messages contain no identity, key, token or business JSON. */
public final class RedisSessionException extends IllegalStateException {
  /** Storage and transaction failures understood by this adapter. */
  public enum Reason {
    /** Read failed before a mutation was submitted. */
    READ_FAILED,
    /** A mutation failed without a known Redis outcome; do not replay it. */
    UNKNOWN_WRITE,
    /** Domain expired, was revoked, or no longer belongs to this handle. */
    STALE_DOMAIN,
    /** Delivery is unknown, expired, or already terminated. */
    INVALID_TOKEN,
    /** Stored JSON or schema is incompatible. */
    INVALID_STATE,
    /** Configured byte, reservation, terminal or connection capacity was exceeded. */
    CAPACITY,
    /** Repeated explicit CAS conflicts exhausted the bounded retry budget. */
    CONTENTION,
    /** Local monotonic operation budget expired before another command could be submitted. */
    BUDGET_EXHAUSTED,
    /** Redis server time moved backwards. */
    CLOCK_REVERSED
  }

  private static final long serialVersionUID = 1L;

  /**
   * Safe failure kind.
   *
   * @serial bounded failure classification, without business state
   */
  private final Reason reason;

  /**
   * Creates a classified failure without including storage contents.
   *
   * @param reason bounded failure kind
   */
  public RedisSessionException(Reason reason) {
    super("Inertia Redis delivery: " + reason);
    this.reason = java.util.Objects.requireNonNull(reason);
  }

  /**
   * Returns the bounded failure kind.
   *
   * @return classification without business data
   */
  public Reason reason() {
    return reason;
  }
}
