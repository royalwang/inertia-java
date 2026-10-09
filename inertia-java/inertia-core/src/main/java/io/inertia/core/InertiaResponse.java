package io.inertia.core;

import java.util.*;

/**
 * Mutable response definition owned by one request and claimed by the renderer only once.
 *
 * <p>Finish builder changes before rendering. Only {@link #claim()} is synchronized; the object
 * does not make concurrent builder mutation safe or freeze setters after claiming. Large-integer
 * and history flags override configuration when set. JSON visits never invoke SSR.
 */
public final class InertiaResponse {
  private final String component;
  private final Props props;
  private int status = 200;
  private boolean ssr = true;
  private boolean ssrRequired;
  private final Map<String, List<String>> headers = new LinkedHashMap<>();
  private final Map<String, Object> viewData = new LinkedHashMap<>();
  private boolean claimed;
  private final Map<String, Object> flash = new LinkedHashMap<>();
  private Boolean encryptHistory;
  private Boolean preserveBigIntegers;
  private boolean clearHistory;

  /**
   * Returns a read-only live view of response-local flash values.
   *
   * @return view of builder flash values; contained business objects are not copied
   */
  public Map<String, Object> flash() {
    return Collections.unmodifiableMap(flash);
  }

  /**
   * Returns the response-local history-encryption override.
   *
   * @return true/false when set, otherwise null to use context/configuration policy
   */
  public Boolean encryptHistory() {
    return encryptHistory;
  }

  /**
   * Returns the response-local large-integer override.
   *
   * @return true/false when set, otherwise null to use configuration policy
   */
  public Boolean preserveBigIntegers() {
    return preserveBigIntegers;
  }

  /**
   * Returns whether this response requests clearing client history.
   *
   * @return response-local flag; stored/context flags may also request clearing
   */
  public boolean clearHistory() {
    return clearHistory;
  }

  /**
   * Adds or replaces a response-local flash value.
   *
   * <p>Unlike {@link InertiaContext#flash(String, Object)}, repeating a key replaces its earlier
   * builder value. During rendering these values overlay stored and context flash.
   *
   * @param key flash key
   * @param value JSON-convertible value, retained until rendering
   * @return this response
   */
  public InertiaResponse flash(String key, Object value) {
    flash.put(key, value);
    return this;
  }

  /**
   * Overrides history encryption for this Page.
   *
   * @param encrypt whether the client should encrypt history state
   * @return this response
   */
  public InertiaResponse encryptHistory(boolean encrypt) {
    encryptHistory = encrypt;
    return this;
  }

  /**
   * Overrides tagged large-integer encoding for this Page's props and flash.
   *
   * @param preserve whether to preserve integers outside JavaScript's safe range
   * @return this response
   */
  public InertiaResponse preserveBigIntegers(boolean preserve) {
    preserveBigIntegers = preserve;
    return this;
  }

  /**
   * Sets the response-local clear-history flag.
   *
   * <p>Setting false does not negate an independently queued or stored clear-history request.
   *
   * @param clear whether this response itself requests clearing history
   * @return this response
   */
  public InertiaResponse clearHistory(boolean clear) {
    clearHistory = clear;
    return this;
  }

  /**
   * Creates a response definition with status 200, SSR enabled, and SSR optional.
   *
   * @param component target component; renderer verifies registry membership
   * @param props immutable prop definitions for this Page
   * @throws NullPointerException if component or props is null
   */
  public InertiaResponse(String component, Props props) {
    this.component = Objects.requireNonNull(component);
    this.props = Objects.requireNonNull(props);
  }

  /**
   * Returns the target component name.
   *
   * @return component supplied at construction
   */
  public String component() {
    return component;
  }

  /**
   * Returns the Page's prop definitions.
   *
   * @return definitions supplied at construction
   */
  public Props props() {
    return props;
  }

  /**
   * Returns the requested status, validated later when creating {@link HttpOutcome}.
   *
   * @return builder status, initially 200
   */
  public int status() {
    return status;
  }

  /**
   * Returns whether HTML rendering may invoke the configured SSR gateway.
   *
   * @return SSR-enabled flag; JSON visits ignore it
   */
  public boolean ssr() {
    return ssr;
  }

  /**
   * Returns whether HTML rendering must succeed through the SSR gateway.
   *
   * @return required-SSR flag; JSON visits do not use SSR
   */
  public boolean ssrRequired() {
    return ssrRequired;
  }

  /**
   * Returns a snapshot of the custom header map.
   *
   * @return unmodifiable map with immutable single-value lists
   */
  public Map<String, List<String>> headers() {
    return Map.copyOf(headers);
  }

  /**
   * Returns a snapshot of additional root-view data.
   *
   * @return unmodifiable map; contained business objects are not copied
   * @throws NullPointerException if a null name or value was queued
   */
  public Map<String, Object> viewData() {
    return Map.copyOf(viewData);
  }

  /**
   * Sets the requested status without immediate validation.
   *
   * @param status HTTP status, checked when the renderer constructs the outcome
   * @return this response
   */
  public InertiaResponse status(int status) {
    this.status = status;
    return this;
  }

  /**
   * Disables SSR for HTML rendering and clears any earlier required-SSR policy.
   *
   * @return this response selecting the CSR shell
   */
  public InertiaResponse withoutSsr() {
    ssr = false;
    ssrRequired = false;
    return this;
  }

  /**
   * Requires successful SSR for HTML rendering, overriding an earlier withoutSsr call.
   *
   * @return this response; JSON visits continue without SSR
   */
  public InertiaResponse requireSsr() {
    ssr = true;
    ssrRequired = true;
    return this;
  }

  /**
   * Adds or replaces data passed to the application root view, outside Page props.
   *
   * @param name root-view data key
   * @param value retained business value; must be non-null when viewData is read
   * @return this response
   */
  public InertiaResponse withViewData(String name, Object value) {
    viewData.put(name, value);
    return this;
  }

  /**
   * Adds or replaces an application-owned response header.
   *
   * <p>Protocol-owned X-Inertia, content-type, content-length, and transfer-encoding headers are
   * rejected here. Remaining header syntax is validated later by {@link HttpOutcome}.
   *
   * @param name case-insensitive header name
   * @param value single header value
   * @return this response
   * @throws IllegalArgumentException if the header is protocol-owned
   * @throws NullPointerException if name or value is null
   */
  public InertiaResponse withHeader(String name, String value) {
    String key = name.toLowerCase(Locale.ROOT);
    if (key.startsWith("x-inertia")
        || Set.of("content-type", "content-length", "transfer-encoding").contains(key))
      throw new IllegalArgumentException("Protocol-owned header: " + name);
    headers.put(key, List.of(value));
    return this;
  }

  /**
   * Claims this definition exactly once for rendering.
   *
   * <p>This guard detects response reuse; it does not freeze builder setters or authorize
   * concurrent mutation. A failed render still consumes the claim.
   *
   * @throws IllegalStateException if already claimed
   */
  public synchronized void claim() {
    if (claimed) throw new IllegalStateException("Response already rendered");
    claimed = true;
  }
}
