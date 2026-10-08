package io.inertia.vite;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.PageCodec;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViteAssetsTest {
  @TempDir Path dir;
  PageCodec codec = new PageCodec();

  Path manifest(String json) throws Exception {
    return Files.writeString(dir.resolve("manifest.json"), json);
  }

  String single(String file) {
    return "{\"src/app.tsx\":{\"file\":\"" + file + "\"}}";
  }

  @Test
  void recursiveImportsDeduplicateCssAndDoNotPreloadEntryInCycles() throws Exception {
    var file =
        manifest(
            """
      {"src/app.tsx":{"file":"assets/app.js","css":["assets/shared.css"],"imports":["chunk"]},
       "chunk":{"file":"assets/chunk.js","css":["assets/shared.css","assets/chunk.css"],"imports":["src/app.tsx"]}}
      """);
    var result = new ViteManifest(file, codec).tags("src/app.tsx");
    assertEquals(
        "<link rel=\"stylesheet\" href=\"/build/assets/shared.css\">"
            + "<link rel=\"stylesheet\" href=\"/build/assets/chunk.css\">"
            + "<link rel=\"modulepreload\" href=\"/build/assets/chunk.js\">"
            + "<script type=\"module\" src=\"/build/assets/app.js\"></script>",
        result);
    assertThrows(
        IllegalArgumentException.class, () -> new ViteManifest(file, codec).tags("missing"));
  }

  @Test
  void invalidManifestFailsAtLoadIncludingUnreachableEntries() throws Exception {
    for (String json :
        new String[] {
          "null",
          "[]",
          "{\"bad\":null}",
          "{\"bad\":{\"file\":1}}",
          "{\"bad\":{\"file\":\"app.js\",\"css\":\"app.css\"}}",
          "{\"bad\":{\"file\":\"app.js\",\"imports\":[\"missing\"]}}",
          "{\"bad\":{\"file\":\"app.js\",\"css\":[null]}}"
        }) {
      Path file = manifest(json);
      assertThrows(IllegalArgumentException.class, () -> new ViteManifest(file, codec), json);
    }
    for (String path :
        new String[] {
          "../secret",
          "a/../secret",
          "/absolute",
          "a//b",
          "./app.js",
          "a/",
          "a'\"",
          "https://evil/x"
        }) {
      Path file =
          manifest(codec.json(codec.object().set("entry", codec.object().put("file", path))));
      assertThrows(IllegalArgumentException.class, () -> new ViteManifest(file, codec), path);
    }
  }

  @Test
  void productionSnapshotsManifestAndIgnoresHotFile() throws Exception {
    Path file = manifest(single("assets/v1.js"));
    Path hot = Files.writeString(dir.resolve("hot"), "not a URL");
    var assets = new ViteAssets(file, hot, false, codec);
    String version = assets.version();
    manifest(single("assets/v2.js"));
    assertNull(assets.hot());
    assertEquals(version, assets.version());
    assertTrue(assets.tags("src/app.tsx").contains("v1.js"));
    assertNotEquals(version, new ViteManifest(file, codec).version());
  }

  @Test
  void developmentRefreshesSubMillisecondMtimeAndFallsBackAfterHotRemoval() throws Exception {
    Path file = manifest(single("assets/v1.js"));
    Files.setLastModifiedTime(file, FileTime.from(java.time.Instant.ofEpochSecond(100, 100_000)));
    Path hot = dir.resolve("hot");
    var assets = new ViteAssets(file, hot, true, codec);
    String version = assets.version();
    manifest(single("assets/v2.js"));
    Files.setLastModifiedTime(file, FileTime.from(java.time.Instant.ofEpochSecond(100, 900_000)));
    assertNotEquals(version, assets.version());
    Files.writeString(hot, "http://127.0.0.1:15173/\n");
    assertEquals("development", assets.version());
    assertTrue(assets.tags("src/app.tsx").contains("http://127.0.0.1:15173/@vite/client"));
    assertThrows(IllegalArgumentException.class, () -> assets.tags("../app.tsx"));
    Files.delete(hot);
    assertTrue(assets.tags("src/app.tsx").contains("v2.js"));
  }

  @Test
  void developmentRejectsNonOriginHotUrls() throws Exception {
    Path file = manifest(single("app.js"));
    Path hot = dir.resolve("hot");
    var assets = new ViteAssets(file, hot, true, codec);
    for (String url :
        new String[] {
          "file:///tmp/app",
          "https://user@host",
          "http://host/path",
          "http://host?token=x",
          "http://host#x",
          "http://host:0",
          "http://host:65536",
          "http://host//"
        }) {
      Files.writeString(hot, url);
      assertThrows(IllegalArgumentException.class, assets::hot, url);
    }
  }

  @Test
  void nonceReachesProductionAndEveryDevelopmentModule() throws Exception {
    Path file = manifest(single("assets/app.js"));
    String nonce = "server_nonce_0123456789";
    var production = new ViteAssets(file, null, false, codec);
    assertTrue(
        production
            .tags("src/app.tsx", nonce)
            .contains("<script nonce=\"" + nonce + "\" type=\"module\""));
    Path hot = Files.writeString(dir.resolve("hot"), "http://127.0.0.1:15173");
    var development = new ViteAssets(file, hot, true, codec);
    String tags = development.tags("src/app.tsx", nonce);
    assertEquals(3, tags.split("nonce=\"" + nonce + "\"", -1).length - 1);
    assertThrows(
        IllegalArgumentException.class, () -> development.tags("src/app.tsx", "\" injection"));
  }

  @Test
  void versionedAssetBaseIsExplicitAndRejectsUnsafeOriginsOrTraversal() throws Exception {
    Path file = manifest(single("assets/app.js"));
    var assets = new ViteAssets(file, null, false, codec, "/build/release-1/");
    assertTrue(assets.tags("src/app.tsx").contains("/build/release-1/assets/app.js"));
    for (String base :
        new String[] {
          "https://host/", "//host/", "/build/../secret/", "/build//", "/build", "/x\"/"
        })
      assertThrows(
          IllegalArgumentException.class, () -> new ViteAssets(file, null, false, codec, base));
  }
}
