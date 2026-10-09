package io.inertia.core;

import java.net.URI;

/** Validates application-configured HTTP targets; never derives targets from request input. */
public final class ConfiguredHttpUrl {
  private ConfiguredHttpUrl() {}

  /**
   * Validates a trusted configured absolute HTTP(S) target.
   *
   * <p>Requires a host and rejects user info, fragments, port zero, and ports above 65535. It does
   * not resolve DNS or enforce an internal-network allowlist; configuration ownership is the trust
   * boundary.
   *
   * @param uri deployment-controlled endpoint URL; path and query are allowed
   * @return unchanged valid URI
   * @throws IllegalArgumentException if the URI is null or violates endpoint syntax
   */
  public static URI endpoint(URI uri) {
    if (uri == null
        || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getPort() == 0
        || uri.getPort() > 65535) {
      throw new IllegalArgumentException("Invalid configured HTTP URL");
    }
    return uri;
  }

  /**
   * Parses a trusted HTTP(S) origin used by a development hot file.
   *
   * @param text origin text, trimmed before parsing; query and non-root paths are rejected
   * @return validated origin without a trailing slash
   * @throws IllegalArgumentException if the URI or origin syntax is invalid
   * @throws NullPointerException if text is null
   */
  public static URI origin(String text) {
    URI uri = endpoint(URI.create(text.trim()));
    if (uri.getRawQuery() != null
        || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))) {
      throw new IllegalArgumentException("Configured hot URL must be an origin");
    }
    String value = uri.toString();
    return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
  }
}
