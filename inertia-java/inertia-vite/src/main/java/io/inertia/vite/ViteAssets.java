package io.inertia.vite;

import io.inertia.core.PageCodec;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Set;

/** Development hot-file behavior is opt-in; production never reads it. */
public final class ViteAssets {
  private final Path manifestFile;
  private final Path hotFile;
  private final boolean development;
  private final PageCodec codec;
  private ViteManifest manifest;
  private long modified = Long.MIN_VALUE;

  public ViteAssets(Path manifestFile, Path hotFile, boolean development, PageCodec codec)
      throws IOException {
    this.manifestFile = manifestFile;
    this.hotFile = hotFile;
    this.development = development;
    this.codec = codec;
    if (!development) manifest = new ViteManifest(manifestFile, codec);
  }

  public String version() {
    if (hot() != null) return "development";
    return manifest().version();
  }

  public String tags(String entry) {
    URI hot = hot();
    if (hot == null) return manifest().tags(entry);
    if (!entry.matches("[A-Za-z0-9_./-]+") || entry.contains("..") || entry.startsWith("/"))
      throw new IllegalArgumentException("Invalid dev entry");
    String url = attribute(hot.toString());
    return "<script type=\"module\" src=\""
        + url
        + "/@vite/client\"></script><script type=\"module\">"
        + "import RefreshRuntime from '"
        + url
        + "/@react-refresh';RefreshRuntime.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>type=>type;window.__vite_plugin_react_preamble_installed__=true;"
        + "</script><script type=\"module\" src=\""
        + url
        + "/"
        + entry
        + "\"></script>";
  }

  private synchronized ViteManifest manifest() {
    if (!development && manifest != null) return manifest;
    try {
      long timestamp = Files.getLastModifiedTime(manifestFile).toMillis();
      if (manifest == null || timestamp != modified) {
        manifest = new ViteManifest(manifestFile, codec);
        modified = timestamp;
      }
      return manifest;
    } catch (IOException error) {
      throw new IllegalStateException("Vite manifest unavailable", error);
    }
  }

  public URI hot() {
    if (!development || hotFile == null || !Files.isRegularFile(hotFile)) return null;
    try {
      URI hot = URI.create(Files.readString(hotFile).trim().replaceAll("/+$", ""));
      if (!Set.of("http", "https").contains(hot.getScheme())
          || hot.getHost() == null
          || hot.getUserInfo() != null
          || hot.getQuery() != null
          || hot.getFragment() != null
          || !(hot.getRawPath().isEmpty() || hot.getRawPath().equals("/")))
        throw new IllegalArgumentException("Invalid hot URL");
      return hot;
    } catch (IOException error) {
      throw new IllegalStateException("Cannot read Vite hot file", error);
    }
  }

  private static String attribute(String text) {
    if (text.contains("'") || text.contains("\""))
      throw new IllegalArgumentException("Unsafe hot URL");
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
