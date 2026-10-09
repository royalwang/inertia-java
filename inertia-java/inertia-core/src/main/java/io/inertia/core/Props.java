package io.inertia.core;

import java.util.*;

/** Ordered definitions; conflicting parent/child paths are rejected before suppliers run. */
public final class Props {
  public enum Source {
    DECLARED,
    INTERNAL_ERRORS,
    CONFIG_SHARED,
    REQUEST_SHARED,
    PAGE
  }

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

  /** Definition-only provenance; not Page props or Inertia metadata. */
  public List<Override> overrides() {
    return overrides;
  }

  public static Props from(Source source, Props props) {
    return new Props(
        props.entries, origins(props.entries, Objects.requireNonNull(source)), props.overrides);
  }

  private static Map<String, Source> origins(Map<String, Prop> entries, Source source) {
    var result = new LinkedHashMap<String, Source>();
    entries.keySet().forEach(path -> result.put(path, source));
    return result;
  }

  public Map<String, Prop> entries() {
    return entries;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Props empty() {
    return builder().build();
  }

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

  public static final class Builder {
    private final Map<String, Prop> entries = new LinkedHashMap<>();

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

    public Props build() {
      validate(entries, origins(entries, Source.DECLARED));
      return new Props(entries);
    }
  }
}
