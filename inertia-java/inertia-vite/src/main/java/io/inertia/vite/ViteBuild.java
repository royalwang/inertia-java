package io.inertia.vite;

import io.inertia.core.PageCodec;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Startup verification of a format-1 client/SSR release receipt and its exact file inventory.
 *
 * <p>The build identity covers recorded content digests, including both the client manifest and SSR
 * entry. Verification detects content mismatch and unrecorded files; it does not authenticate the
 * publisher. Keep verified directories immutable afterwards: this object does not monitor changes.
 */
public final class ViteBuild {
  private final String buildId;
  private final Map<String, String> clientFiles;

  /**
   * Verifies {@code build.json}, file digests, containment, manifest references, and inventory.
   *
   * @param directory release root containing {@code build.json}, {@code client/}, and {@code ssr/}
   * @param codec JSON codec used for the receipt and canonical build-identity calculation
   * @throws IOException if a receipt, directory, or recorded file cannot be read or resolved
   * @throws IllegalArgumentException if the receipt, identity, content, paths, or inventory is
   *     invalid
   */
  public ViteBuild(Path directory, PageCodec codec) throws IOException {
    Path root = directory.toRealPath();
    var receipt = codec.read(Files.readString(root.resolve("build.json")));
    if (receipt == null
        || !receipt.isObject()
        || !receipt.path("format").isIntegralNumber()
        || receipt.path("format").intValue() != 1
        || !receipt.path("files").isObject()
        || !receipt.path("buildId").isTextual())
      throw new IllegalArgumentException("Invalid Vite build receipt");
    var files = new TreeMap<String, String>();
    receipt
        .get("files")
        .fields()
        .forEachRemaining(
            entry -> {
              String path = ViteManifest.safe(entry.getKey());
              if (!(path.startsWith("client/") || path.startsWith("ssr/"))
                  || !entry.getValue().isTextual()
                  || !entry.getValue().textValue().matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid Vite build file");
              files.put(path, entry.getValue().textValue());
            });
    if (!files.containsKey("client/.vite/manifest.json") || !files.containsKey("ssr/ssr.js"))
      throw new IllegalArgumentException("Incomplete Vite build receipt");
    var client = new TreeMap<String, String>();
    files.forEach(
        (path, digest) -> {
          if (path.startsWith("client/")) client.put(path.substring(7), digest);
        });
    clientFiles = Map.copyOf(client);
    var canonical = codec.object().put("format", 1);
    canonical.set("files", codec.value(files));
    buildId = hash(codec.json(canonical).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (!buildId.equals(receipt.get("buildId").textValue()))
      throw new IllegalArgumentException("Vite build id mismatch");
    for (var entry : files.entrySet()) {
      Path file = root.resolve(entry.getKey()).toRealPath();
      if (!file.startsWith(root)
          || !Files.isRegularFile(file)
          || !entry.getValue().equals(hash(Files.readAllBytes(file))))
        throw new IllegalArgumentException("Vite build content mismatch: " + entry.getKey());
    }
    new ViteManifest(root.resolve("client/.vite/manifest.json"), codec);
    var manifest = codec.read(Files.readString(root.resolve("client/.vite/manifest.json")));
    for (var node : manifest) {
      requireAsset(files, node.get("file").textValue());
      for (String field : List.of("css", "assets")) {
        var values = node.get(field);
        if (values == null) continue;
        if (!values.isArray()) throw new IllegalArgumentException("Invalid Vite asset list");
        for (var value : values) {
          if (!value.isTextual()) throw new IllegalArgumentException("Invalid Vite asset path");
          requireAsset(files, value.textValue());
        }
      }
    }
    try (var paths = Files.walk(root.resolve("client"))) {
      verifyInventory(root, paths, files);
    }
    try (var paths = Files.walk(root.resolve("ssr"))) {
      verifyInventory(root, paths, files);
    }
  }

  private static void requireAsset(Map<String, String> files, String path) {
    if (!files.containsKey("client/" + ViteManifest.safe(path)))
      throw new IllegalArgumentException("Unrecorded manifest asset");
  }

  private static void verifyInventory(
      Path root, java.util.stream.Stream<Path> paths, Map<String, String> files) {
    paths
        .filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        .forEach(
            path -> {
              if (!files.containsKey(root.relativize(path).toString().replace('\\', '/')))
                throw new IllegalArgumentException("Unrecorded Vite build file");
            });
  }

  /**
   * Verifies an independently published client tree against this receipt's client inventory.
   *
   * @param releaseDirectory root of the published client assets, without the {@code client/} prefix
   * @throws IOException if the tree or a recorded asset cannot be read or resolved
   * @throws IllegalArgumentException if an asset differs, escapes the root, or is unrecorded
   */
  public void verifyClientAssets(Path releaseDirectory) throws IOException {
    Path root = releaseDirectory.toRealPath();
    for (var entry : clientFiles.entrySet()) {
      Path file = root.resolve(entry.getKey()).toRealPath();
      if (!file.startsWith(root)
          || !Files.isRegularFile(file)
          || !entry.getValue().equals(hash(Files.readAllBytes(file))))
        throw new IllegalArgumentException("Published asset content mismatch");
    }
    try (var paths = Files.walk(root)) {
      paths
          .filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
          .forEach(
              path -> {
                if (!clientFiles.containsKey(root.relativize(path).toString().replace('\\', '/')))
                  throw new IllegalArgumentException("Unrecorded published asset");
              });
    }
  }

  /**
   * Returns the verified shared client/SSR identity from the canonical receipt.
   *
   * @return lowercase SHA-256 build identifier suitable for the Page version and renderer check
   */
  public String buildId() {
    return buildId;
  }

  private static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
