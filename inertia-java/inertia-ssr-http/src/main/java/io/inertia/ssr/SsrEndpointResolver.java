package io.inertia.ssr;

import io.inertia.core.ConfiguredHttpUrl;
import io.inertia.core.InertiaRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/**
 * Selects a trusted renderer endpoint without making a network request.
 *
 * <p>Exclusions are applied first. Development uses a valid hot-file origin plus {@code
 * /__inertia_ssr}; otherwise an optional bundle must exist before the production endpoint can be
 * returned. Configuration comes from the application, never an incoming request header.
 */
public final class SsrEndpointResolver {
  private final URI production;
  private final Path hotFile;
  private final Path bundle;
  private final boolean development;
  private final List<String> excluded;

  /**
   * Creates an endpoint selector from trusted application configuration.
   *
   * @param production validated HTTP(S) renderer URL, including the desired render path
   * @param hotFile optional development file containing a trusted HTTP(S) origin
   * @param bundle optional bundle path whose absence makes production rendering unavailable
   * @param development whether to inspect an existing hot file before production selection
   * @param excluded copied exact-path or trailing-star prefix rules; leading slash is ignored
   * @throws IllegalArgumentException if the production URL violates configured-URL policy
   */
  public SsrEndpointResolver(
      URI production, Path hotFile, Path bundle, boolean development, List<String> excluded) {
    this.production = validate(production);
    this.hotFile = hotFile;
    this.bundle = bundle;
    this.development = development;
    this.excluded = List.copyOf(excluded);
  }

  /**
   * Resolves policy for this request; malformed or unreadable hot files produce unavailability.
   *
   * @param request immutable snapshot supplying the path for exclusion matching
   * @return selected trusted URL, or null for an excluded/unavailable renderer
   */
  public URI resolve(InertiaRequest request) {
    String path = request.path().replaceFirst("^/", "");
    for (String rule : excluded) {
      String pattern = rule.replaceFirst("^/", "");
      if (pattern.endsWith("*")
          ? path.startsWith(pattern.substring(0, pattern.length() - 1))
          : path.equals(pattern)) return null;
    }
    if (development && hotFile != null && Files.isRegularFile(hotFile)) {
      try {
        URI hot = ConfiguredHttpUrl.origin(Files.readString(hotFile));
        return hot.resolve("/__inertia_ssr");
      } catch (IOException | IllegalArgumentException error) {
        return null;
      }
    }
    if (bundle != null && !Files.isRegularFile(bundle)) return null;
    return production;
  }

  static URI validate(URI uri) {
    return ConfiguredHttpUrl.endpoint(uri);
  }
}
