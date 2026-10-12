package io.inertia.redis;

import io.lettuce.core.SslOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Immutable TLS trust selection; never disables certificate or hostname verification. */
public final class RedisTlsOptions {
  private final boolean enabled;
  private final Path trustStore;
  private final char[] password;

  private RedisTlsOptions(boolean enabled, Path trustStore, char[] password) {
    this.enabled = enabled;
    this.trustStore = trustStore;
    this.password = password == null ? null : password.clone();
  }

  /**
   * Selects the legacy plaintext transport.
   *
   * @return explicit plaintext selection used by the legacy backend constructor
   */
  public static RedisTlsOptions disabled() {
    return new RedisTlsOptions(false, null, null);
  }

  /**
   * Selects TLS with the JDK trust configuration.
   *
   * @return TLS using the JDK's configured system trust anchors
   */
  public static RedisTlsOptions systemTrust() {
    return new RedisTlsOptions(true, null, null);
  }

  /**
   * Selects a PKCS12 trust store containing trusted public certificates.
   *
   * @param path readable local PKCS12 file; read by Lettuce when opening a TLS connection
   * @param password optional password, defensively copied; caller retains ownership of its array
   * @return TLS trust selection without exposing password accessors
   * @throws IllegalArgumentException if the file is not readable
   * @throws NullPointerException if path is null
   */
  public static RedisTlsOptions trustStore(Path path, char[] password) {
    Objects.requireNonNull(path);
    if (!Files.isRegularFile(path) || !Files.isReadable(path))
      throw new IllegalArgumentException("Redis TLS trust store must be a readable local file");
    return new RedisTlsOptions(true, path.toAbsolutePath().normalize(), password);
  }

  boolean enabled() {
    return enabled;
  }

  SslOptions sslOptions(Duration timeout) {
    var builder =
        SslOptions.builder()
            .jdkSslProvider()
            .protocols("TLSv1.3", "TLSv1.2")
            .handshakeTimeout(timeout)
            .keyStoreType("PKCS12");
    if (trustStore != null)
      builder.truststore(
          SslOptions.Resource.from(trustStore.toFile()),
          password == null ? null : password.clone());
    return builder.build();
  }

  /**
   * @return redacted TLS mode without trust-store path or credential material
   */
  @Override
  public String toString() {
    return "RedisTlsOptions[enabled="
        + enabled
        + ", trust="
        + (trustStore == null ? "system" : "configured")
        + "]";
  }
}
