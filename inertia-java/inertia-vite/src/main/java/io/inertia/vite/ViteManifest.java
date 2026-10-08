package io.inertia.vite;

import com.fasterxml.jackson.databind.JsonNode;
import io.inertia.core.PageCodec;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Production assets are snapshotted at startup, together with their version hash. */
public final class ViteManifest {
  private final JsonNode manifest;
  private final String version;

  public ViteManifest(Path file, PageCodec codec) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    manifest = codec.read(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    if (!manifest.isObject()) throw new IllegalArgumentException("Manifest must be an object");
    try {
      version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public String version() {
    return version;
  }

  public String tags(String entry) {
    if (!manifest.has(entry)) throw new IllegalArgumentException("Missing Vite entry: " + entry);
    var css = new LinkedHashSet<String>();
    var imports = new LinkedHashSet<String>();
    visit(entry, new HashSet<>(), css, imports);
    var out = new StringBuilder();
    for (String path : css)
      out.append("<link rel=\"stylesheet\" href=\"/build/").append(safe(path)).append("\">");
    for (String path : imports)
      out.append("<link rel=\"modulepreload\" href=\"/build/").append(safe(path)).append("\">");
    return out.append("<script type=\"module\" src=\"/build/")
        .append(safe(manifest.get(entry).path("file").asText()))
        .append("\"></script>")
        .toString();
  }

  private void visit(String entry, Set<String> seen, Set<String> css, Set<String> imports) {
    if (!seen.add(entry)) return;
    var node = manifest.get(entry);
    if (node == null || !node.isObject())
      throw new IllegalArgumentException("Missing imported entry: " + entry);
    node.path("css").forEach(n -> css.add(n.asText()));
    node.path("imports")
        .forEach(
            n -> {
              visit(n.asText(), seen, css, imports);
              imports.add(manifest.get(n.asText()).path("file").asText());
            });
  }

  private static String safe(String path) {
    if (!path.matches("[A-Za-z0-9_./-]+")
        || path.startsWith("/")
        || Arrays.asList(path.split("/")).contains(".."))
      throw new IllegalArgumentException("Unsafe asset path");
    return path;
  }
}
