package io.inertia.core;

/** Developer-facing definition diagnostics. Paths are schema keys, never serialized values. */
public final class PropDefinitionException extends IllegalArgumentException {
  /** Definition planning error category. */
  public enum Kind {
    /** A path is null, malformed, contains whitespace, or exceeds the nesting limit. */
    INVALID_PATH,
    /** Both a parent path and one of its descendant paths are defined. */
    PARENT_CHILD_CONFLICT
  }

  /**
   * Definition error category.
   *
   * @serial invalid-path or parent/child-conflict classification
   */
  private final Kind kind;

  /**
   * First diagnostic schema path.
   *
   * @serial invalid path or conflicting parent, without serialized prop values
   */
  private final String firstPath;

  /**
   * Second diagnostic schema path.
   *
   * @serial conflicting descendant, or null for an invalid single path
   */
  private final String secondPath;

  /**
   * First definition origin.
   *
   * @serial provenance of the first path
   */
  private final Props.Source firstSource;

  /**
   * Second definition origin.
   *
   * @serial provenance of the descendant, or null when not applicable
   */
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

  /**
   * Returns the definition error category.
   *
   * @return invalid-path or parent/child-conflict classification
   */
  public Kind kind() {
    return kind;
  }

  /**
   * Returns the invalid path or the conflicting parent path.
   *
   * @return diagnostic schema path, without any serialized prop value
   */
  public String firstPath() {
    return firstPath;
  }

  /**
   * Returns the conflicting descendant when applicable.
   *
   * @return descendant path, or null for an invalid single path
   */
  public String secondPath() {
    return secondPath;
  }

  /**
   * Returns the origin of the first diagnostic definition.
   *
   * @return provenance for the first path
   */
  public Props.Source firstSource() {
    return firstSource;
  }

  /**
   * Returns the origin of the conflicting descendant when applicable.
   *
   * @return provenance for the second path, or null for an invalid single path
   */
  public Props.Source secondSource() {
    return secondSource;
  }
}
