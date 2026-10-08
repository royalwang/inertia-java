package io.inertia.core;

import java.net.URI;

/** Validates application-configured HTTP targets; never derives targets from request input. */
public final class ConfiguredHttpUrl {
  private ConfiguredHttpUrl() {}

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
