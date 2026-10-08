package io.inertia.core;

/** Opt-in JSON logging, using only the already-redacted event. */
public final class LoggingInertiaObserver implements InertiaObserver {
  private final System.Logger logger;
  private final PageCodec codec = new PageCodec();

  public LoggingInertiaObserver() {
    this(System.getLogger("io.inertia.observations"));
  }

  public LoggingInertiaObserver(System.Logger logger) {
    this.logger = java.util.Objects.requireNonNull(logger);
  }

  @Override
  public void observe(Event event) {
    logger.log(System.Logger.Level.INFO, () -> codec.value(event).toString());
  }
}
