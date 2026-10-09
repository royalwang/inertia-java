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
    boolean allErrors,
    boolean exposeSharedPropKeys,
    Function<InertiaRequest, String> urlResolver) {
  public InertiaConfig {
    Objects.requireNonNull(version);
    Objects.requireNonNull(rootView);
    Objects.requireNonNull(shared);
    Objects.requireNonNull(urlResolver);
    rootId = requireRootId(rootId);
    components = Set.copyOf(components);
    if (components.isEmpty())
      throw new IllegalArgumentException("Register at least one page component");
  }

  /** Original nine-argument API retains request URL and shared key exposure. */
  public InertiaConfig(
      Supplier<String> version,
      String rootId,
      Set<String> components,
      RootView rootView,
      SsrGateway gateway,
      Function<InertiaRequest, Props> shared,
      boolean preserveBigIntegers,
      boolean encryptHistory,
      boolean allErrors) {
    this(
        version,
        rootId,
        components,
        rootView,
        gateway,
        shared,
        preserveBigIntegers,
        encryptHistory,
        allErrors,
        true,
        InertiaRequest::url);
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
        all,
        exposeSharedPropKeys,
        urlResolver);
  }

  public InertiaConfig withUrlResolver(Function<InertiaRequest, String> resolver) {
    return new InertiaConfig(
        version,
        rootId,
        components,
        rootView,
        gateway,
        shared,
        preserveBigIntegers,
        encryptHistory,
        allErrors,
        exposeSharedPropKeys,
        resolver);
  }

  public InertiaConfig withSharedPropKeys(boolean expose) {
    return new InertiaConfig(
        version,
        rootId,
        components,
        rootView,
        gateway,
        shared,
        preserveBigIntegers,
        encryptHistory,
        allErrors,
        expose,
        urlResolver);
  }

  /** Trusted synchronous presentation callback; does not rewrite request routing/redirects. */
  public String pageUrl(InertiaRequest request) {
    String url =
        Objects.requireNonNull(urlResolver.apply(request), "Page URL resolver returned null");
    if (url.isBlank()
        || url.length() > 8192
        || url.chars().anyMatch(c -> Character.isISOControl(c)))
      throw new IllegalArgumentException("Invalid resolved Page URL");
    return url;
  }

  public static String requireRootId(String value) {
    if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_-]*"))
      throw new IllegalArgumentException("Unsafe root id");
    return value;
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
