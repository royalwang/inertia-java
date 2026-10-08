package io.inertia.ssr;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.InertiaRequest;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SsrEndpointResolverTest {
  @TempDir Path dir;
  URI production = URI.create("http://127.0.0.1:13714/render");

  InertiaRequest request(String path) {
    return new InertiaRequest(
        "GET", URI.create("https://app.test" + path), Map.of("Authorization", "ignored"));
  }

  @Test
  void exclusionsUsePathWithoutQueryAndApplyBeforeHot() throws Exception {
    Path hot = Files.writeString(dir.resolve("hot"), "http://localhost:15173");
    var resolver =
        new SsrEndpointResolver(production, hot, null, true, List.of("/admin/*", "private"));
    assertNull(resolver.resolve(request("/admin/users?show=1")));
    assertNull(resolver.resolve(request("/private?show=1")));
    assertEquals(
        URI.create("http://localhost:15173/__inertia_ssr"),
        resolver.resolve(request("/private-other")));
  }

  @Test
  void developmentRequiresOriginAndInvalidHotFallsBackWithoutContactingProduction()
      throws Exception {
    Path hot = dir.resolve("hot");
    var resolver = new SsrEndpointResolver(production, hot, null, true, List.of());
    for (String url :
        List.of(
            "http://host/path",
            "http://host?query=x",
            "http://user@host",
            "http://host#x",
            "http://host:65536",
            "http://host:0",
            "file:///tmp")) {
      Files.writeString(hot, url);
      assertNull(resolver.resolve(request("/users")), url);
    }
    Files.writeString(hot, "https://[::1]:15173/\n");
    assertEquals(
        URI.create("https://[::1]:15173/__inertia_ssr"), resolver.resolve(request("/users")));
  }

  @Test
  void productionIgnoresHotAndMissingBundleDisablesSsr() throws Exception {
    Path hot = Files.writeString(dir.resolve("hot"), "http://evil.invalid");
    Path bundle = dir.resolve("ssr.js");
    var resolver = new SsrEndpointResolver(production, hot, bundle, false, List.of());
    assertNull(resolver.resolve(request("/users")));
    Files.writeString(bundle, "bundle");
    assertEquals(production, resolver.resolve(request("/users")));
    Files.delete(bundle);
    assertNull(resolver.resolve(request("/users")));
  }

  @Test
  void invalidProductionTargetFailsConfiguration() {
    for (String value :
        List.of(
            "file:///tmp",
            "http://user@host/render",
            "http://host/render#x",
            "http://host:0/render",
            "http://host:65536/render")) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new SsrEndpointResolver(URI.create(value), null, null, false, List.of()),
          value);
    }
  }
}
