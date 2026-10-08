package io.inertia.vite;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.PageCodec;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ViteBuildTest {
  @TempDir Path root;
  PageCodec codec = new PageCodec();
  Map<String, String> hashes = new TreeMap<>();

  String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  void file(String path, String text) throws Exception {
    Path target = root.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, text);
    hashes.put(path, hash(Files.readAllBytes(target)));
  }

  String receipt() throws Exception {
    var canonical = codec.object().put("format", 1);
    canonical.set("files", codec.value(hashes));
    String id = hash(codec.json(canonical).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Files.writeString(root.resolve("build.json"), codec.json(canonical.put("buildId", id)));
    return id;
  }

  @BeforeEach
  void setup() throws Exception {
    file("client/.vite/manifest.json", "{\"src/app.tsx\":{\"file\":\"assets/app.js\"}}");
    file("client/assets/app.js", "client");
    file("ssr/ssr.js", "server");
    receipt();
  }

  @Test
  void stableBuildIdBindsBothClientAndServer() throws Exception {
    String id = new ViteBuild(root, codec).buildId();
    assertEquals(receipt(), id);
    file("ssr/ssr.js", "new server");
    assertNotEquals(id, receipt());
    assertEquals(receipt(), new ViteBuild(root, codec).buildId());
  }

  @Test
  void mixedMissingAndExtraArtifactsFail() throws Exception {
    Files.writeString(root.resolve("ssr/ssr.js"), "different release");
    assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
    file("ssr/ssr.js", "server");
    receipt();
    Files.writeString(root.resolve("client/extra.js"), "extra");
    assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
    Files.delete(root.resolve("client/extra.js"));
    Files.delete(root.resolve("client/assets/app.js"));
    assertThrows(java.io.IOException.class, () -> new ViteBuild(root, codec));
  }

  @Test
  void invalidReceiptAndUnrecordedManifestReferencesFail() throws Exception {
    Files.writeString(root.resolve("build.json"), "{}");
    assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
    receipt();
    var record = codec.read(Files.readString(root.resolve("build.json")));
    ((com.fasterxml.jackson.databind.node.ObjectNode) record).put("buildId", "0".repeat(64));
    Files.writeString(root.resolve("build.json"), codec.json(record));
    assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
    file("client/.vite/manifest.json", "{\"src/app.tsx\":{\"file\":\"assets/missing.js\"}}");
    receipt();
    assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
  }

  @Test
  void traversalAndExternalSymlinkFail() throws Exception {
    hashes.put("client/../outside", "0".repeat(64));
    receipt();
    assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
    hashes.remove("client/../outside");
    receipt();
    Path outside = Files.createTempFile("inertia-build-outside", ".js");
    try {
      Files.writeString(outside, "client");
      Files.delete(root.resolve("client/assets/app.js"));
      Files.createSymbolicLink(root.resolve("client/assets/app.js"), outside);
      assertThrows(IllegalArgumentException.class, () -> new ViteBuild(root, codec));
    } finally {
      Files.deleteIfExists(outside);
    }
  }

  @Test
  void publishedClientArchiveMustMatchContentAndFullInventory() throws Exception {
    var build = new ViteBuild(root, codec);
    Path archive = root.resolve("published");
    try (var paths = Files.walk(root.resolve("client"))) {
      for (Path source : paths.toList()) {
        Path target = archive.resolve(root.resolve("client").relativize(source));
        if (Files.isDirectory(source)) Files.createDirectories(target);
        else Files.copy(source, target);
      }
    }
    build.verifyClientAssets(archive);
    Files.writeString(archive.resolve("extra.js"), "extra");
    assertThrows(IllegalArgumentException.class, () -> build.verifyClientAssets(archive));
    Files.delete(archive.resolve("extra.js"));
    Files.writeString(archive.resolve("assets/app.js"), "mixed");
    assertThrows(IllegalArgumentException.class, () -> build.verifyClientAssets(archive));
    Files.delete(archive.resolve("assets/app.js"));
    assertThrows(java.io.IOException.class, () -> build.verifyClientAssets(archive));
  }
}
