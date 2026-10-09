package io.inertia.vite;

import io.inertia.core.ConfiguredHttpUrl;
import io.inertia.core.CspNonce;
import io.inertia.core.PageCodec;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;

/**
 * Application-scoped Vite asset selection for production snapshots or opt-in development.
 *
 * <p>Production reads the manifest at construction and ignores the hot file. Development consults
 * the trusted hot file per call and reloads a fallback manifest when its modification time changes.
 * Separate calls to {@link #version()} and {@link #tags(String)} are not an atomic snapshot when
 * development files change. This helper owns no background workers or closeable resources.
 */
public final class ViteAssets {
  private final Path manifestFile;
  private final Path hotFile;
  private final boolean development;
  private final PageCodec codec;
  private ViteManifest manifest;
  private final String assetBase;
  private FileTime modified;

  /**
   * Creates an asset helper with the {@code /build/} asset base.
   *
   * @param manifestFile client manifest used in production and as the development fallback
   * @param hotFile trusted development-origin file, or {@code null} to disable it
   * @param development whether to enable hot-file selection and manifest refresh
   * @param codec JSON codec
   * @throws IOException if the production manifest cannot be read
   * @throws IllegalArgumentException if the production manifest is invalid
   */
  public ViteAssets(Path manifestFile, Path hotFile, boolean development, PageCodec codec)
      throws IOException {
    this(manifestFile, hotFile, development, codec, "/build/");
  }

  /**
   * Creates an asset helper with an explicit same-origin asset base.
   *
   * @param manifestFile client manifest used in production and as the development fallback
   * @param hotFile trusted development-origin file, or {@code null} to disable it
   * @param development whether to enable hot-file selection and manifest refresh
   * @param codec JSON codec
   * @param assetBase absolute path starting and ending with a slash, including {@code /}
   * @throws IOException if the production manifest cannot be read
   * @throws IllegalArgumentException if the base or production manifest is invalid
   */
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

  /**
   * Returns the active development marker or manifest digest.
   *
   * @return {@code development} when a hot origin is active, otherwise the manifest SHA-256
   * @throws IllegalStateException if a development file cannot be read
   * @throws IllegalArgumentException if a hot origin or fallback manifest is invalid
   */
  public String version() {
    if (hot() != null) return "development";
    return manifest().version();
  }

  /**
   * Builds active asset tags without a CSP nonce.
   *
   * @param entry manifest key or safe development entry path
   * @return HTML for the selected development or production assets
   * @throws IllegalArgumentException if the entry, manifest, or hot origin is invalid
   * @throws IllegalStateException if a development file cannot be read
   */
  public String tags(String entry) {
    return tags(entry, null);
  }

  /**
   * Builds active asset tags with an optional nonce.
   *
   * <p>Hot mode emits the Vite client, React refresh preamble, and entry module. A missing hot file
   * selects the manifest; an unreadable or invalid existing hot file fails instead of silently
   * falling back. Only deployment-controlled files should supply the development origin.
   *
   * @param entry manifest key or safe relative development entry path
   * @param nonce CSP nonce for all generated tags, or {@code null} to omit it
   * @return selected asset HTML
   * @throws IllegalArgumentException if the entry, nonce, manifest, or hot origin is invalid
   * @throws IllegalStateException if a development file cannot be read
   */
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

  /**
   * Reads the trusted development origin, when enabled and present.
   *
   * @return validated HTTP(S) origin, or {@code null} in production or when no regular hot file
   *     exists
   * @throws IllegalStateException if the hot file cannot be read
   * @throws IllegalArgumentException if its content is not a valid configured origin
   */
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
