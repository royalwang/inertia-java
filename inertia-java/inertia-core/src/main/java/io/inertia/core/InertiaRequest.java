package io.inertia.core;

import java.net.URI;
import java.util.*;

/**
 * Immutable HTTP metadata captured before asynchronous work begins.
 *
 * <p>Proxy reconstruction and trusted nonce/request-ID selection belong to the framework adapter.
 * Header keys are lowercased and copied; values are limited to 8192 characters. This snapshot does
 * not authenticate incoming protocol headers or impose the configured renderer-URL policy.
 *
 * @param method HTTP method, normalized to uppercase
 * @param fullUrl absolute reconstructed URL with a host
 * @param headers non-null header names and values; copied with lowercase keys
 * @param nonce trusted server CSP nonce, or null
 * @param requestId server-selected identifier matching {@code [A-Za-z0-9][A-Za-z0-9_.-]{0,63}}
 */
public record InertiaRequest(
    String method, URI fullUrl, Map<String, String> headers, String nonce, String requestId) {
  /**
   * Captures metadata with a generated request ID and no CSP nonce.
   *
   * @param method HTTP method
   * @param fullUrl absolute reconstructed URL with a host
   * @param headers captured header values
   * @throws IllegalArgumentException if the URL or a header value is invalid
   */
  public InertiaRequest(String method, URI fullUrl, Map<String, String> headers) {
    this(method, fullUrl, headers, null);
  }

  /**
   * Captures metadata with a generated request ID and an optional trusted nonce.
   *
   * @param method HTTP method
   * @param fullUrl absolute reconstructed URL with a host
   * @param headers captured header values
   * @param nonce server CSP nonce, or null
   * @throws IllegalArgumentException if the URL, nonce, or a header value is invalid
   */
  public InertiaRequest(String method, URI fullUrl, Map<String, String> headers, String nonce) {
    this(method, fullUrl, headers, nonce, UUID.randomUUID().toString());
  }

  /**
   * Validates metadata and copies headers before publication to provider tasks.
   *
   * @throws IllegalArgumentException if the request ID, nonce, URL, or header length is invalid
   * @throws NullPointerException if required metadata, header keys, or values are null
   */
  public InertiaRequest {
    if (requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))
      throw new IllegalArgumentException("Unsafe server request id");
    nonce = CspNonce.require(nonce);
    method = Objects.requireNonNull(method).toUpperCase(Locale.ROOT);
    Objects.requireNonNull(fullUrl);
    if (!fullUrl.isAbsolute() || fullUrl.getHost() == null)
      throw new IllegalArgumentException("Absolute URL required");
    var copy = new LinkedHashMap<String, String>();
    headers.forEach(
        (k, v) -> {
          if (v.length() > 8192) throw new IllegalArgumentException("Header too long");
          copy.put(k.toLowerCase(Locale.ROOT), v);
        });
    headers = Collections.unmodifiableMap(copy);
  }

  /**
   * Looks up a header case-insensitively.
   *
   * @param name header name
   * @return captured value, or null if absent
   */
  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Detects an Inertia visit by presence of the {@code X-Inertia} header.
   *
   * @return true when the header exists, regardless of its textual value
   */
  public boolean isInertia() {
    return headers.containsKey("x-inertia");
  }

  /**
   * Returns the raw path and optional raw query for Page presentation.
   *
   * @return URL without origin or fragment
   */
  public String url() {
    return fullUrl.getRawPath()
        + (fullUrl.getRawQuery() == null ? "" : "?" + fullUrl.getRawQuery());
  }

  /**
   * Returns the reconstructed URL's raw path without query or fragment.
   *
   * @return raw path, preserving percent escapes
   */
  public String path() {
    return fullUrl.getRawPath();
  }

  /**
   * Checks whether the partial-component header exactly matches a component.
   *
   * @param component target component name
   * @return true when the component matches; other protocol checks belong to the caller
   */
  public boolean isPartial(String component) {
    return component.equals(header("x-inertia-partial-component"));
  }

  /**
   * Parses a comma-separated header into distinct nonempty trimmed values in input order.
   *
   * @param name header name
   * @return unmodifiable values, or an empty list for an absent/blank header
   * @throws IllegalArgumentException if more than 128 distinct values remain
   */
  public List<String> list(String name) {
    String value = header(name);
    if (value == null || value.isBlank()) return List.of();
    var values =
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    if (values.size() > 128) throw new IllegalArgumentException("Too many header paths");
    return values;
  }

  /**
   * Detects an exact case-insensitive {@code prefetch} value in a supported purpose header.
   *
   * @return true when purpose, sec-purpose, or x-moz declares prefetch
   */
  public boolean isPrefetch() {
    return List.of("purpose", "sec-purpose", "x-moz").stream()
        .anyMatch(h -> "prefetch".equalsIgnoreCase(header(h)));
  }

  /**
   * Resolves the Referer and accepts only the reconstructed request's scheme, host, and port.
   *
   * <p>Targets with user info or invalid URI syntax fall back to {@code /}. The returned target has
   * no origin or fragment. Correct trusted-proxy URL reconstruction is required for origin
   * comparison.
   *
   * @return same-origin raw path/query, or {@code /} for an absent or rejected Referer
   */
  public String safeBack() {
    String referer = header("referer");
    if (referer == null) return "/";
    try {
      URI target = fullUrl.resolve(referer);
      if (Objects.equals(target.getScheme(), fullUrl.getScheme())
          && Objects.equals(target.getHost(), fullUrl.getHost())
          && effectivePort(target) == effectivePort(fullUrl)
          && target.getUserInfo() == null)
        return target.getRawPath()
            + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
    } catch (IllegalArgumentException ignored) {
    }
    return "/";
  }

  private static int effectivePort(URI uri) {
    return uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
  }
}
