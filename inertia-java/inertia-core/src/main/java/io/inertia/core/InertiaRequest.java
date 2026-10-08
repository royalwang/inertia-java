package io.inertia.core;

import java.net.URI;
import java.util.*;

/** Immutable HTTP snapshot. Proxy reconstruction belongs to the framework adapter. */
public record InertiaRequest(
    String method, URI fullUrl, Map<String, String> headers, String nonce, String requestId) {
  public InertiaRequest(String method, URI fullUrl, Map<String, String> headers) {
    this(method, fullUrl, headers, null);
  }

  public InertiaRequest(String method, URI fullUrl, Map<String, String> headers, String nonce) {
    this(method, fullUrl, headers, nonce, UUID.randomUUID().toString());
  }

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

  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }

  public boolean isInertia() {
    return headers.containsKey("x-inertia");
  }

  public String url() {
    return fullUrl.getRawPath()
        + (fullUrl.getRawQuery() == null ? "" : "?" + fullUrl.getRawQuery());
  }

  public String path() {
    return fullUrl.getRawPath();
  }

  public boolean isPartial(String component) {
    return component.equals(header("x-inertia-partial-component"));
  }

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

  public boolean isPrefetch() {
    return List.of("purpose", "sec-purpose", "x-moz").stream()
        .anyMatch(h -> "prefetch".equalsIgnoreCase(header(h)));
  }

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
