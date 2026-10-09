package io.inertia.core;

import java.util.*;
import java.util.function.*;

/**
 * Application-scoped render policy and trusted extension callbacks.
 *
 * <p>The component set is copied. Callbacks and the gateway are retained, so their captured state
 * and resource lifecycle remain application-owned. This record does not create an executor, SSR
 * client, asset server, or security policy for navigation destinations.
 *
 * @param version synchronous supplier of the Page asset version
 * @param rootId HTML root identifier shared with browser and renderer
 * @param components registered Page component names, copied by the constructor
 * @param rootView application-owned synchronous HTML shell
 * @param gateway application-owned SSR provider, or null to select CSR
 * @param shared synchronous factory of shared prop definitions for each request
 * @param preserveBigIntegers whether to emit the tagged large-integer representation
 * @param encryptHistory default client history-encryption flag
 * @param allErrors whether validation props contain all messages per field
 * @param exposeSharedPropKeys whether metadata advertises shared top-level keys
 * @param urlResolver synchronous Page-URL presentation callback; does not alter routing or
 *     redirects
 */
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
  /**
   * Validates required callbacks, root ID, and the nonempty component registry.
   *
   * @throws NullPointerException if a required callback, component set, or set member is null
   * @throws IllegalArgumentException if the root ID is unsafe or the component set is empty
   */
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

  /**
   * Creates render policy retaining request URL presentation and shared-key exposure.
   *
   * @param version synchronous supplier of the Page asset version
   * @param rootId HTML root identifier shared with browser and renderer
   * @param components registered Page component names, copied by the constructor
   * @param rootView application-owned synchronous HTML shell
   * @param gateway application-owned SSR provider, or null to select CSR
   * @param shared synchronous factory of shared prop definitions for each request
   * @param preserveBigIntegers whether to emit the tagged large-integer representation
   * @param encryptHistory default client history-encryption flag
   * @param allErrors whether validation props contain all messages per field
   * @throws IllegalArgumentException if the root ID or component set is invalid
   */
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

  /**
   * Creates policy using first-message validation errors, request URL, and shared-key exposure.
   *
   * @param version synchronous supplier of the Page asset version
   * @param rootId HTML root identifier shared with browser and renderer
   * @param components registered Page component names, copied by the constructor
   * @param rootView application-owned synchronous HTML shell
   * @param gateway application-owned SSR provider, or null to select CSR
   * @param shared synchronous factory of shared prop definitions for each request
   * @param preserveBigIntegers whether to emit the tagged large-integer representation
   * @param encryptHistory default client history-encryption flag
   * @throws IllegalArgumentException if the root ID or component set is invalid
   */
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

  /**
   * Copies this policy with a different validation-presentation default.
   *
   * @param all whether to include all messages per field
   * @return new configuration retaining the same callbacks and resource ownership
   */
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

  /**
   * Copies this policy with a trusted synchronous Page-URL callback.
   *
   * @param resolver non-null callback; result is checked by {@link #pageUrl(InertiaRequest)}
   * @return new configuration with the supplied callback
   * @throws NullPointerException if resolver is null
   */
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

  /**
   * Copies this policy with a different shared-key disclosure setting.
   *
   * @param expose whether to emit shared top-level key names in Page metadata
   * @return new configuration; shared prop values and selection are unaffected
   */
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

  /**
   * Invokes and validates the synchronous Page-URL presentation callback.
   *
   * <p>This presentation value does not rewrite request routing, version-conflict locations, or
   * redirects. The callback must finish promptly; it is not scheduled on the props executor.
   *
   * @param request captured request metadata
   * @return non-blank URL of at most 8192 characters without ISO control characters
   * @throws NullPointerException if the callback returns null
   * @throws IllegalArgumentException if the returned value fails presentation validation
   */
  public String pageUrl(InertiaRequest request) {
    String url =
        Objects.requireNonNull(urlResolver.apply(request), "Page URL resolver returned null");
    if (url.isBlank()
        || url.length() > 8192
        || url.chars().anyMatch(c -> Character.isISOControl(c)))
      throw new IllegalArgumentException("Invalid resolved Page URL");
    return url;
  }

  /**
   * Validates an HTML root identifier without changing it.
   *
   * @param value identifier starting with an ASCII letter, followed by letters, digits, underscore,
   *     or hyphen
   * @return unchanged valid identifier
   * @throws IllegalArgumentException if value is null or unsafe
   */
  public static String requireRootId(String value) {
    if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_-]*"))
      throw new IllegalArgumentException("Unsafe root id");
    return value;
  }

  /**
   * Creates minimal CSR policy for examples or an application-supplied shell replacement.
   *
   * <p>Uses root {@code app}, an empty shared-prop factory, no gateway, and no big-integer or
   * history flags. {@link RootView#minimal()} adds no frontend assets.
   *
   * @param version fixed Page asset version
   * @param components registered component names
   * @return minimal render configuration
   * @throws IllegalArgumentException if no component is registered
   */
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
