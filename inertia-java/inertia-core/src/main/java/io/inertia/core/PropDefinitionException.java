package io.inertia.core;

/** Developer-facing definition diagnostics. Paths are schema keys, never serialized values. */
public final class PropDefinitionException extends IllegalArgumentException {
  public enum Kind {
    INVALID_PATH,
    PARENT_CHILD_CONFLICT
  }

  private final Kind kind;
  private final String firstPath;
  private final String secondPath;
  private final Props.Source firstSource;
  private final Props.Source secondSource;

  PropDefinitionException(
      Kind kind,
      String firstPath,
      String secondPath,
      Props.Source firstSource,
      Props.Source secondSource) {
    super(
        kind == Kind.INVALID_PATH
            ? "Invalid prop path: " + firstPath
            : "Conflicting prop paths: "
                + firstPath
                + " ("
                + firstSource
                + ") / "
                + secondPath
                + " ("
                + secondSource
                + ")");
    this.kind = kind;
    this.firstPath = firstPath;
    this.secondPath = secondPath;
    this.firstSource = firstSource;
    this.secondSource = secondSource;
  }

  public Kind kind() {
    return kind;
  }

  public String firstPath() {
    return firstPath;
  }

  public String secondPath() {
    return secondPath;
  }

  public Props.Source firstSource() {
    return firstSource;
  }

  public Props.Source secondSource() {
    return secondSource;
  }
}
