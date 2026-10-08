package io.inertia.vite;

import com.fasterxml.jackson.databind.JsonNode;
import io.inertia.core.CspNonce;
import io.inertia.core.PageCodec;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Production assets are snapshotted at startup, together with their version hash. */
public final class ViteManifest {
  private final JsonNode manifest;
  private final String version;
  private final String assetBase;

  public ViteManifest(Path file, PageCodec codec) throws IOException {
    this(file, codec, "/build/");
  }

  public ViteManifest(Path file, PageCodec codec, String assetBase) throws IOException {
    this.assetBase = base(assetBase);
    byte[] bytes = Files.readAllBytes(file);
    manifest = codec.read(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    if (manifest == null || !manifest.isObject())
      throw new IllegalArgumentException("Manifest must be an object");
    validate();
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
    return tags(entry, null);
  }

  public String tags(String entry, String nonce) {
    String attribute = CspNonce.attribute(nonce);
    if (!manifest.has(entry)) throw new IllegalArgumentException("Missing Vite entry: " + entry);
    var css = new LinkedHashSet<String>();
    var imports = new LinkedHashSet<String>();
    visit(entry, new HashSet<>(), css, imports);
    imports.remove(manifest.get(entry).get("file").textValue());
    var out = new StringBuilder();
    for (String path : css)
      out.append("<link")
          .append(attribute)
          .append(" rel=\"stylesheet\" href=\"")
          .append(assetBase)
          .append(safe(path))
          .append("\">");
    for (String path : imports)
      out.append("<link")
          .append(attribute)
          .append(" rel=\"modulepreload\" href=\"")
          .append(assetBase)
          .append(safe(path))
          .append("\">");
    return out.append("<script")
        .append(attribute)
        .append(" type=\"module\" src=\"")
        .append(assetBase)
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

  private void validate() {
    manifest
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode node = entry.getValue();
              if (!node.isObject() || !node.path("file").isTextual())
                throw new IllegalArgumentException("Invalid Vite entry: " + entry.getKey());
              safe(node.get("file").textValue());
              for (String field : List.of("css", "imports")) {
                JsonNode values = node.get(field);
                if (values == null) continue;
                if (!values.isArray()) throw new IllegalArgumentException("Invalid Vite " + field);
                for (JsonNode value : values) {
                  if (!value.isTextual())
                    throw new IllegalArgumentException("Invalid Vite " + field);
                  if (field.equals("css")) safe(value.textValue());
                  else if (!manifest.has(value.textValue()))
                    throw new IllegalArgumentException(
                        "Missing imported entry: " + value.textValue());
                }
              }
            });
  }

  static String base(String value) {
    if (value == null || !value.startsWith("/") || value.startsWith("//") || !value.endsWith("/"))
      throw new IllegalArgumentException("Invalid asset base");
    if (!value.equals("/")) safe(value.substring(1, value.length() - 1));
    return value;
  }

  static String safe(String path) {
    if (path == null || !path.matches("[A-Za-z0-9_./-]+") || path.startsWith("/"))
      throw new IllegalArgumentException("Unsafe asset path");
    for (String segment : path.split("/", -1))
      if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
        throw new IllegalArgumentException("Unsafe asset path");
    return path;
  }
}
