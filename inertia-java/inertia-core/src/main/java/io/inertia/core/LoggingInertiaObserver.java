package io.inertia.core;

/** Opt-in JSON logging, using only the already-redacted event. */
public final class LoggingInertiaObserver implements InertiaObserver {
  private final System.Logger logger;
  private final PageCodec codec = new PageCodec();

  /** Creates an INFO-level JSON sink using logger name {@code io.inertia.observations}. */
  public LoggingInertiaObserver() {
    this(System.getLogger("io.inertia.observations"));
  }

  /**
   * Creates a JSON sink without taking ownership of the application logger.
   *
   * @param logger non-null logging sink
   * @throws NullPointerException if logger is null
   */
  public LoggingInertiaObserver(System.Logger logger) {
    this.logger = java.util.Objects.requireNonNull(logger);
  }

  /**
   * Logs the event as JSON at INFO level; fields are not additionally redacted here.
   *
   * @param event library-safe event, or custom event whose publisher has removed sensitive data
   */
  @Override
  public void observe(Event event) {
    logger.log(System.Logger.Level.INFO, () -> codec.value(event).toString());
  }
}
