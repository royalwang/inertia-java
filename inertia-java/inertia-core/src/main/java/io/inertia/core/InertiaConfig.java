package io.inertia.core;

import java.util.*;
import java.util.function.*;

public record InertiaConfig(
    Supplier<String> version,
    String rootId,
    Set<String> components,
    RootView rootView,
    SsrGateway gateway,
    Function<InertiaRequest, Props> shared,
    boolean preserveBigIntegers,
    boolean encryptHistory,
    boolean allErrors) {
  public InertiaConfig {
    Objects.requireNonNull(version);
    Objects.requireNonNull(rootView);
    Objects.requireNonNull(shared);
    if (!rootId.matches("[A-Za-z][A-Za-z0-9_-]*"))
      throw new IllegalArgumentException("Unsafe root id");
    components = Set.copyOf(components);
    if (components.isEmpty())
      throw new IllegalArgumentException("Register at least one page component");
  }

  public InertiaConfig(
      Supplier<String> version,
      String rootId,
      Set<String> components,
      RootView rootView,
      SsrGateway gateway,
      Function<InertiaRequest, Props> shared,
      boolean preserveBigIntegers,
      boolean encryptHistory) {
    this(
        version,
        rootId,
        components,
        rootView,
        gateway,
        shared,
        preserveBigIntegers,
        encryptHistory,
        false);
  }

  public InertiaConfig withAllErrors(boolean all) {
    return new InertiaConfig(
        version,
        rootId,
        components,
        rootView,
        gateway,
        shared,
        preserveBigIntegers,
        encryptHistory,
        all);
  }

  public static InertiaConfig basic(String version, Set<String> components) {
    return new InertiaConfig(
        () -> version,
        "app",
        components,
        RootView.minimal(),
        null,
        r -> Props.empty(),
        false,
        false);
  }
}
