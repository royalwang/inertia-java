package io.inertia.core;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * Immutable prop definition combining a value source with loading and client metadata policy.
 *
 * <p>Fluent operations return new definitions. Providers and mutable captured values remain the
 * application's responsibility; capture request data before scheduling. Merge and once options
 * instruct the client and provide neither server caching nor authorization.
 *
 * @param source value or provider source; non-null
 * @param loading initial/partial request loading policy; non-null
 * @param group deferred request group; non-null
 * @param always whether this prop bypasses partial selection
 * @param rescued whether a failed deferred value is reported as rescued metadata
 * @param mergeOptions client merge policy, or null to replace normally
 * @param onceOptions client reuse policy, or null for normal loading
 * @param scroll whether the resolved provider value must be a {@link ScrollPage}
 */
public record Prop(
    Source source,
    Loading loading,
    String group,
    boolean always,
    boolean rescued,
    Merge mergeOptions,
    Once onceOptions,
    boolean scroll) {
  /**
   * Creates a definition without merge, once, or scroll options.
   *
   * @param source non-null value or provider source
   * @param loading non-null loading policy
   * @param group non-null deferred group
   * @param always whether to retain the prop regardless of partial selection
   * @param rescued whether to rescue deferred provider failures
   * @throws NullPointerException if source, loading, or group is null
   */
  public Prop(Source source, Loading loading, String group, boolean always, boolean rescued) {
    this(source, loading, group, always, rescued, null, null, false);
  }

  /**
   * Client merge metadata; path lists are copied at construction.
   *
   * @param deep whether to request deep merge
   * @param prependRoot whether to prepend the root array when no nested paths are set
   * @param appendAt relative nested paths whose arrays should append
   * @param prependAt relative nested paths whose arrays should prepend
   * @param matchOn relative identity paths used to match existing items
   */
  public record Merge(
      boolean deep,
      boolean prependRoot,
      java.util.List<String> appendAt,
      java.util.List<String> prependAt,
      java.util.List<String> matchOn) {
    /**
     * Copies the path lists so later caller mutations cannot change this definition.
     *
     * @throws NullPointerException if a list or list element is null
     */
    public Merge {
      appendAt = java.util.List.copyOf(appendAt);
      prependAt = java.util.List.copyOf(prependAt);
      matchOn = java.util.List.copyOf(matchOn);
    }
  }

  /**
   * Client reuse metadata; expiry is calculated by the resolver clock.
   *
   * @param key reuse identity, or null to use the prop path
   * @param ttl non-negative lifetime, or null for no expiry
   * @param fresh whether to ignore the client's already-loaded declaration
   */
  public record Once(String key, java.time.Duration ttl, boolean fresh) {
    /**
     * Validates the optional lifetime.
     *
     * @throws IllegalArgumentException if the lifetime is negative
     */
    public Once {
      if (ttl != null && ttl.isNegative())
        throw new IllegalArgumentException("Negative onceOptions TTL");
    }
  }

  /** Determines whether a provider participates in the initial visit or selected reloads. */
  public enum Loading {
    /** Included on the initial visit, subject to once policy and partial selection on reloads. */
    EAGER,
    /** Omitted initially and available on a matching partial request. */
    OPTIONAL,
    /** Omitted initially, advertised by group, and available on a matching partial request. */
    DEFERRED
  }

  /** Synchronous provider invoked on the configured props executor when selected. */
  @FunctionalInterface
  public interface Task {
    /**
     * Computes the selected value under the request's total resolution budget.
     *
     * @return JSON-convertible value, nested value, or ScrollPage when configured for scroll
     * @throws Exception if the provider cannot produce its value
     */
    Object get() throws Exception;
  }

  /** Closed set of supported literal, nested, synchronous, and asynchronous value sources. */
  public sealed interface Source permits Literal, Nested, Computed, Async {}

  /**
   * Literal value converted to JSON during resolution.
   *
   * @param value value to convert, including null; mutable values are not copied here
   */
  public record Literal(Object value) implements Source {}

  /**
   * Nested prop definitions recursively planned by the resolver.
   *
   * @param props nested definitions
   */
  public record Nested(Props props) implements Source {}

  /**
   * Synchronous value source scheduled only when selected.
   *
   * @param task provider to invoke
   */
  public record Computed(Task task) implements Source {}

  /**
   * Asynchronous source whose factory is invoked on the configured prop executor.
   *
   * @param task factory returning a non-null request-owned completion stage
   */
  public record Async(java.util.function.Supplier<CompletionStage<?>> task) implements Source {}

  /**
   * Validates the required source, loading policy, and deferred group.
   *
   * <p>Use fluent factories to obtain consistent option combinations. This constructor does not
   * validate every policy combination or eagerly invoke providers.
   *
   * @throws NullPointerException if source, loading, or group is null
   */
  public Prop {
    Objects.requireNonNull(source);
    Objects.requireNonNull(loading);
    Objects.requireNonNull(group);
  }

  /**
   * Creates an eager literal, or nested definitions when the value is {@link Props}.
   *
   * @param value value to convert during resolution, including null
   * @return eager definition in the default group
   */
  public static Prop value(Object value) {
    return new Prop(
        value instanceof Props p ? new Nested(p) : new Literal(value),
        Loading.EAGER,
        "default",
        false,
        false);
  }

  /**
   * Creates an eager provider evaluated only when request selection includes it.
   *
   * @param task non-null synchronous provider
   * @return eager computed definition
   * @throws NullPointerException if task is null
   */
  public static Prop lazy(Task task) {
    return new Prop(
        new Computed(Objects.requireNonNull(task)), Loading.EAGER, "default", false, false);
  }

  /**
   * Creates an eager asynchronous provider; its factory runs on the configured props executor.
   *
   * <p>Capture request data before scheduling and return a request-owned stage whose cancellation
   * can signal its underlying work. A null stage fails resolution. Do not reuse a shared future
   * whose cancellation would affect another request.
   *
   * @param task factory for the selected provider's completion stage
   * @return eager asynchronous definition
   */
  public static Prop async(java.util.function.Supplier<CompletionStage<?>> task) {
    return new Prop(new Async(task), Loading.EAGER, "default", false, false);
  }

  /**
   * Creates a provider omitted initially and evaluated on a matching partial reload.
   *
   * @param task synchronous provider
   * @return optional definition
   */
  public static Prop optional(Task task) {
    return new Prop(new Computed(task), Loading.OPTIONAL, "default", false, false);
  }

  /**
   * Creates a deferred provider advertised in the default group on the initial visit.
   *
   * @param task synchronous provider for a matching deferred reload
   * @return deferred definition
   */
  public static Prop defer(Task task) {
    return new Prop(new Computed(task), Loading.DEFERRED, "default", false, false);
  }

  /**
   * Creates an eager value retained regardless of partial selection.
   *
   * @param value literal value or nested Props definitions
   * @return always-included definition
   */
  public static Prop always(Object value) {
    return new Prop(value(value).source, Loading.EAGER, "default", true, false);
  }

  /**
   * Selects the client deferred-request group.
   *
   * @param value non-blank group name
   * @return deferred definition with the new group
   * @throws IllegalArgumentException if this prop is not deferred or the name is blank
   */
  public Prop group(String value) {
    if (loading != Loading.DEFERRED || value.isBlank())
      throw new IllegalArgumentException("Group requires deferred prop");
    return new Prop(source, loading, value, always, rescued, mergeOptions, onceOptions, scroll);
  }

  /**
   * Requests append merging at the prop root, resetting prior nested and matching paths.
   *
   * @return definition with a normal root merge policy
   */
  public Prop merge() {
    return copy(
        new Merge(false, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
        onceOptions,
        scroll);
  }

  /**
   * Requests deep client merging, resetting prior nested and matching paths.
   *
   * @return definition with a deep merge policy
   */
  public Prop deepMerge() {
    return copy(
        new Merge(true, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
        onceOptions,
        scroll);
  }

  /**
   * Requests prepending at the prop root, retaining match paths and resetting nested paths.
   *
   * @return definition with a normal root prepend policy
   */
  public Prop prepend() {
    return copy(
        new Merge(false, true, java.util.List.of(), java.util.List.of(), matches()),
        onceOptions,
        scroll);
  }

  /**
   * Adds a relative nested array path to append and selects normal merging.
   *
   * @param path valid dot-separated path within this prop's value
   * @return definition retaining existing nested merge and match paths
   * @throws PropDefinitionException if the path is invalid
   */
  public Prop appendAt(String path) {
    return mergeAt(path, false);
  }

  /**
   * Adds a relative nested array path to prepend and selects normal merging.
   *
   * @param path valid dot-separated path within this prop's value
   * @return definition retaining existing nested merge and match paths
   * @throws PropDefinitionException if the path is invalid
   */
  public Prop prependAt(String path) {
    return mergeAt(path, true);
  }

  private Prop mergeAt(String path, boolean prepend) {
    checkPath(path);
    var appends =
        new java.util.ArrayList<>(
            mergeOptions == null ? java.util.List.<String>of() : mergeOptions.appendAt());
    var prepends =
        new java.util.ArrayList<>(
            mergeOptions == null ? java.util.List.<String>of() : mergeOptions.prependAt());
    (prepend ? prepends : appends).add(path);
    return copy(new Merge(false, false, appends, prepends, matches()), onceOptions, scroll);
  }

  /**
   * Adds a client item-identity path to an existing merge policy.
   *
   * @param path valid relative dot-separated identity path
   * @return definition with the additional match path
   * @throws IllegalStateException if no merge policy exists
   * @throws PropDefinitionException if the path is invalid
   */
  public Prop matchOn(String path) {
    checkPath(path);
    if (mergeOptions == null) throw new IllegalStateException("matchOn requires mergeOptions");
    var paths = new java.util.ArrayList<>(matches());
    paths.add(path);
    return copy(
        new Merge(
            mergeOptions.deep(),
            mergeOptions.prependRoot(),
            mergeOptions.appendAt(),
            mergeOptions.prependAt(),
            paths),
        onceOptions,
        scroll);
  }

  private java.util.List<String> matches() {
    return mergeOptions == null ? java.util.List.of() : mergeOptions.matchOn();
  }

  /**
   * Enables client reuse by prop path, resetting prior key, expiry, and freshness options.
   *
   * @return definition with no client expiry and normal once loading
   */
  public Prop once() {
    return copy(mergeOptions, new Once(null, null, false), scroll);
  }

  /**
   * Sets a client reuse key while retaining existing lifetime and freshness.
   *
   * @param key non-blank reuse identity
   * @return definition with once loading enabled
   * @throws IllegalArgumentException if the key is blank
   */
  public Prop onceAs(String key) {
    if (key.isBlank()) throw new IllegalArgumentException("Empty onceOptions key");
    return copy(
        mergeOptions,
        new Once(
            key,
            onceOptions == null ? null : onceOptions.ttl(),
            onceOptions != null && onceOptions.fresh()),
        scroll);
  }

  /**
   * Sets the once lifetime while retaining existing reuse identity and freshness.
   *
   * @param ttl non-negative lifetime, or null to omit expiry
   * @return definition with once loading enabled; expiry uses the resolver clock
   * @throws IllegalArgumentException if the lifetime is negative
   */
  public Prop until(java.time.Duration ttl) {
    return copy(
        mergeOptions,
        new Once(
            onceOptions == null ? null : onceOptions.key(),
            ttl,
            onceOptions != null && onceOptions.fresh()),
        scroll);
  }

  /**
   * Forces resolution despite the client's once-loaded declaration, retaining key and lifetime.
   *
   * @return definition with fresh once loading enabled
   */
  public Prop fresh() {
    return copy(
        mergeOptions,
        new Once(
            onceOptions == null ? null : onceOptions.key(),
            onceOptions == null ? null : onceOptions.ttl(),
            true),
        scroll);
  }

  /**
   * Creates an eager scroll value with normal merging of its data wrapper.
   *
   * @param page scroll data and pagination metadata
   * @return eager scroll definition
   */
  public static Prop scroll(ScrollPage page) {
    return value(page)
        .copy(
            new Merge(false, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
            null,
            true);
  }

  /**
   * Creates a selected synchronous provider that must return a {@link ScrollPage}.
   *
   * @param task non-null provider of scroll data and pagination metadata
   * @return eager computed scroll definition with normal merging
   * @throws NullPointerException if task is null
   */
  public static Prop scrollWith(Task task) {
    return lazy(task)
        .copy(
            new Merge(false, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
            null,
            true);
  }

  private Prop copy(Merge mergeOptions, Once onceOptions, boolean scroll) {
    return new Prop(source, loading, group, always, rescued, mergeOptions, onceOptions, scroll);
  }

  private static void checkPath(String path) {
    Props.builder().put(path, 1).build();
  }

  /**
   * Reports a deferred provider failure as rescued metadata instead of failing the whole Page.
   *
   * @return deferred definition with failure rescue enabled
   * @throws IllegalArgumentException if this prop is not deferred
   */
  public Prop rescue() {
    if (loading != Loading.DEFERRED)
      throw new IllegalArgumentException("Rescue requires deferred prop");
    return new Prop(source, loading, group, always, true, mergeOptions, onceOptions, scroll);
  }
}
