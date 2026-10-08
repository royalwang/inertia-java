package io.inertia.ssr;

import io.inertia.core.ConfiguredHttpUrl;
import io.inertia.core.InertiaRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Endpoint configuration is trusted application input, never an incoming request header. */
public final class SsrEndpointResolver {
  private final URI production;
  private final Path hotFile;
  private final Path bundle;
  private final boolean development;
  private final List<String> excluded;

  public SsrEndpointResolver(
      URI production, Path hotFile, Path bundle, boolean development, List<String> excluded) {
    this.production = validate(production);
    this.hotFile = hotFile;
    this.bundle = bundle;
    this.development = development;
    this.excluded = List.copyOf(excluded);
  }

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
