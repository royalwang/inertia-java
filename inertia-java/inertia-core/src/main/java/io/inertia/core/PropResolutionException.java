package io.inertia.core;

/**
 * Provider failure associated with a definition path, preserving its underlying cause.
 *
 * <p>The message identifies schema paths, not prop values. Causes can contain application details
 * and should remain in authorized diagnostics rather than client error props.
 */
public final class PropResolutionException extends RuntimeException {
  /**
   * Provider schema path.
   *
   * @serial definition path whose resolution failed
   */
  private final String path;

  /**
   * Creates a failure identifying the selected provider path.
   *
   * @param path definition path that could not resolve
   * @param cause provider/conversion failure, or null
   */
  public PropResolutionException(String path, Throwable cause) {
    super("Cannot resolve prop: " + path, cause);
    this.path = path;
  }

  /**
   * Returns the failing definition path.
   *
   * @return path supplied at construction
   */
  public String path() {
    return path;
  }
}
