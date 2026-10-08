package io.inertia.vite;

import io.inertia.core.ConfiguredHttpUrl;
import io.inertia.core.CspNonce;
import io.inertia.core.PageCodec;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;

/** Development hot-file behavior is opt-in; production never reads it. */
public final class ViteAssets {
  private final Path manifestFile;
  private final Path hotFile;
  private final boolean development;
  private final PageCodec codec;
  private ViteManifest manifest;
  private final String assetBase;
  private FileTime modified;

  public ViteAssets(Path manifestFile, Path hotFile, boolean development, PageCodec codec)
      throws IOException {
    this(manifestFile, hotFile, development, codec, "/build/");
  }

  public ViteAssets(
      Path manifestFile, Path hotFile, boolean development, PageCodec codec, String assetBase)
      throws IOException {
    this.assetBase = ViteManifest.base(assetBase);
    this.manifestFile = manifestFile;
    this.hotFile = hotFile;
    this.development = development;
    this.codec = codec;
    if (!development) manifest = new ViteManifest(manifestFile, codec, assetBase);
  }

  public String version() {
    if (hot() != null) return "development";
    return manifest().version();
  }

  public String tags(String entry) {
    return tags(entry, null);
  }

  public String tags(String entry, String nonce) {
    String nonceAttribute = CspNonce.attribute(nonce);
    URI hot = hot();
    if (hot == null) return manifest().tags(entry, nonce);
    ViteManifest.safe(entry);
    String url = attribute(hot.toString());
    return "<script"
        + nonceAttribute
        + " type=\"module\" src=\""
        + url
        + "/@vite/client\"></script><script"
        + nonceAttribute
        + " type=\"module\">"
        + "import RefreshRuntime from '"
        + url
        + "/@react-refresh';RefreshRuntime.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>type=>type;window.__vite_plugin_react_preamble_installed__=true;"
        + "</script><script"
        + nonceAttribute
        + " type=\"module\" src=\""
        + url
        + "/"
        + entry
        + "\"></script>";
  }

  private synchronized ViteManifest manifest() {
    if (!development && manifest != null) return manifest;
    try {
      FileTime timestamp = Files.getLastModifiedTime(manifestFile);
      if (manifest == null || !timestamp.equals(modified)) {
        manifest = new ViteManifest(manifestFile, codec, assetBase);
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
      return ConfiguredHttpUrl.origin(Files.readString(hotFile));
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
