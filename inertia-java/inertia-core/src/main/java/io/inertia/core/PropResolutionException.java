package io.inertia.core;

public final class PropResolutionException extends RuntimeException {
  private final String path;

  public PropResolutionException(String path, Throwable cause) {
    super("Cannot resolve prop: " + path, cause);
    this.path = path;
  }

  public String path() {
    return path;
  }
}
