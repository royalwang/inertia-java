package io.inertia.core;

import java.util.*;

/** Mutable builder, owned by one request and rendered only once. */
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

  public Map<String, Object> flash() {
    return Collections.unmodifiableMap(flash);
  }

  public Boolean encryptHistory() {
    return encryptHistory;
  }

  public Boolean preserveBigIntegers() {
    return preserveBigIntegers;
  }

  public boolean clearHistory() {
    return clearHistory;
  }

  public InertiaResponse flash(String key, Object value) {
    flash.put(key, value);
    return this;
  }

  public InertiaResponse encryptHistory(boolean encrypt) {
    encryptHistory = encrypt;
    return this;
  }

  public InertiaResponse preserveBigIntegers(boolean preserve) {
    preserveBigIntegers = preserve;
    return this;
  }

  public InertiaResponse clearHistory(boolean clear) {
    clearHistory = clear;
    return this;
  }

  public InertiaResponse(String component, Props props) {
    this.component = Objects.requireNonNull(component);
    this.props = Objects.requireNonNull(props);
  }

  public String component() {
    return component;
  }

  public Props props() {
    return props;
  }

  public int status() {
    return status;
  }

  public boolean ssr() {
    return ssr;
  }

  /** Whether HTML rendering must succeed through the SSR gateway. JSON visits do not use SSR. */
  public boolean ssrRequired() {
    return ssrRequired;
  }

  public Map<String, List<String>> headers() {
    return Map.copyOf(headers);
  }

  public Map<String, Object> viewData() {
    return Map.copyOf(viewData);
  }

  public InertiaResponse status(int status) {
    this.status = status;
    return this;
  }

  public InertiaResponse withoutSsr() {
    ssr = false;
    ssrRequired = false;
    return this;
  }

  /** Require server-rendered HTML; overrides an earlier withoutSsr call. */
  public InertiaResponse requireSsr() {
    ssr = true;
    ssrRequired = true;
    return this;
  }

  public InertiaResponse withViewData(String name, Object value) {
    viewData.put(name, value);
    return this;
  }

  public InertiaResponse withHeader(String name, String value) {
    String key = name.toLowerCase(Locale.ROOT);
    if (key.startsWith("x-inertia")
        || Set.of("content-type", "content-length", "transfer-encoding").contains(key))
      throw new IllegalArgumentException("Protocol-owned header: " + name);
    headers.put(key, List.of(value));
    return this;
  }

  public synchronized void claim() {
    if (claimed) throw new IllegalStateException("Response already rendered");
    claimed = true;
  }
}
