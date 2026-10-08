package io.inertia.core;

import java.util.*;

/** Ordered definitions; conflicting parent/child paths are rejected before suppliers run. */
public final class Props {
  private final Map<String, Prop> entries;

  private Props(Map<String, Prop> entries) {
    this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
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
    var builder = builder();
    for (Props props : sources) builder.entries.putAll(props.entries);
    return builder.build();
  }

  public static final class Builder {
    private final Map<String, Prop> entries = new LinkedHashMap<>();

    public Builder put(String path, Object value) {
      if (!path.matches("[^.\\s]+(\\.[^.\\s]+)*") || path.split("\\.").length > 32)
        throw new IllegalArgumentException("Invalid prop path: " + path);
      entries.put(path, value instanceof Prop p ? p : Prop.value(value));
      return this;
    }

    public Props build() {
      for (String a : entries.keySet())
        for (String b : entries.keySet())
          if (!a.equals(b) && b.startsWith(a + "."))
            throw new IllegalArgumentException("Conflicting prop paths: " + a + " / " + b);
      return new Props(entries);
    }
  }
}
