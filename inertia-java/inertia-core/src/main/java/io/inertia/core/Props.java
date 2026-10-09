package io.inertia.core;

import java.util.*;

/** Ordered definitions; conflicting parent/child paths are rejected before suppliers run. */
public final class Props {
  /** Definition provenance for override diagnostics; not serialized as Page props. */
  public enum Source {
    /** Explicit builder input before a layer assigns a more specific origin. */
    DECLARED,
    /** Renderer-generated validation error definitions. */
    INTERNAL_ERRORS,
    /** Definitions returned by the application configuration's shared callback. */
    CONFIG_SHARED,
    /** Definitions shared on the request context. */
    REQUEST_SHARED,
    /** Definitions supplied for the target Page. */
    PAGE
  }

  /**
   * Diagnostic description of an exact-path replacement while overlaying definition layers.
   *
   * @param path definition path, without its value
   * @param previous origin of the replaced definition
   * @param replacement origin of the new definition
   */
  public record Override(String path, Source previous, Source replacement) {}

  private final Map<String, Prop> entries;
  private final Map<String, Source> origins;
  private final List<Override> overrides;

  private Props(Map<String, Prop> entries) {
    this(entries, origins(entries, Source.DECLARED), List.of());
  }

  private Props(Map<String, Prop> entries, Map<String, Source> origins, List<Override> overrides) {
    this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    this.origins = Collections.unmodifiableMap(new LinkedHashMap<>(origins));
    this.overrides = List.copyOf(overrides);
  }

  /**
   * Returns immutable definition-only override provenance.
   *
   * @return replacements retained from overlays; not Page props or Inertia metadata
   */
  public List<Override> overrides() {
    return overrides;
  }

  /**
   * Copies definition metadata with a single assigned origin, retaining existing override history.
   *
   * @param source non-null origin assigned to every definition in this set
   * @param props definitions to relabel without invoking providers
   * @return immutable definition set referencing the same Prop objects
   * @throws NullPointerException if source or props is null
   */
  public static Props from(Source source, Props props) {
    return new Props(
        props.entries, origins(props.entries, Objects.requireNonNull(source)), props.overrides);
  }

  private static Map<String, Source> origins(Map<String, Prop> entries, Source source) {
    var result = new LinkedHashMap<String, Source>();
    entries.keySet().forEach(path -> result.put(path, source));
    return result;
  }

  /**
   * Returns definitions in insertion order.
   *
   * @return unmodifiable map; providers and captured business values are not copied
   */
  public Map<String, Prop> entries() {
    return entries;
  }

  /**
   * Creates a mutable definition builder.
   *
   * @return request/application-owned builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates an empty immutable definition set.
   *
   * @return empty definitions
   */
  public static Props empty() {
    return builder().build();
  }

  /**
   * Overlays ordered definition sets, with later exact-path definitions taking precedence.
   *
   * <p>Exact replacement retains the path's original insertion position and records provenance. A
   * parent/child path conflict across layers fails before any provider runs.
   *
   * @param sources definition layers, from lowest to highest precedence
   * @return immutable ordered definitions and replacement history
   * @throws PropDefinitionException if parent and child paths coexist
   */
  public static Props overlay(Props... sources) {
    var entries = new LinkedHashMap<String, Prop>();
    var origins = new LinkedHashMap<String, Source>();
    var overrides = new ArrayList<Override>();
    for (Props props : sources) {
      overrides.addAll(props.overrides);
      props.entries.forEach(
          (path, value) -> {
            if (entries.containsKey(path))
              overrides.add(new Override(path, origins.get(path), props.origins.get(path)));
            entries.put(path, value);
            origins.put(path, props.origins.get(path));
          });
    }
    validate(entries, origins);
    return new Props(entries, origins, overrides);
  }

  private static void validate(Map<String, Prop> entries, Map<String, Source> origins) {
    for (String a : entries.keySet())
      for (String b : entries.keySet())
        if (!a.equals(b) && b.startsWith(a + "."))
          throw new PropDefinitionException(
              PropDefinitionException.Kind.PARENT_CHILD_CONFLICT,
              a,
              b,
              origins.get(a),
              origins.get(b));
  }

  /**
   * Mutable definition collector; build an immutable snapshot before resolving it.
   *
   * <p>Repeated exact keys replace their previous definition. Parent/child conflicts are checked at
   * build time; path syntax is checked when adding each value.
   */
  public static final class Builder {
    /** Creates an empty ordered definition collector. */
    public Builder() {}

    private final Map<String, Prop> entries = new LinkedHashMap<>();

    /**
     * Adds or replaces one definition without invoking providers.
     *
     * @param path nonempty dot-separated path without whitespace or empty segments, at most 32
     *     segments
     * @param value existing Prop or a literal/nested Props value wrapped through {@link
     *     Prop#value(Object)}
     * @return this builder
     * @throws PropDefinitionException if the path syntax is invalid
     */
    public Builder put(String path, Object value) {
      if (path == null || !path.matches("[^.\\s]+(\\.[^.\\s]+)*") || path.split("\\.").length > 32)
        throw new PropDefinitionException(
            PropDefinitionException.Kind.INVALID_PATH,
            String.valueOf(path),
            null,
            Source.DECLARED,
            null);
      entries.put(path, value instanceof Prop p ? p : Prop.value(value));
      return this;
    }

    /**
     * Validates parent/child conflicts and snapshots the ordered definition map.
     *
     * @return immutable definitions independent of later builder changes
     * @throws PropDefinitionException if parent and child paths coexist
     */
    public Props build() {
      validate(entries, origins(entries, Source.DECLARED));
      return new Props(entries);
    }
  }
}
