package io.inertia.boot;

import io.inertia.redis.RedisTlsOptions;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional Redis delivery TLS trust; host Spring Session TLS is configured separately.
 *
 * @param enabled explicit TLS activation, default false
 * @param trustStore optional readable PKCS12 trust-store path; absence uses JDK system trust
 * @param trustStorePassword optional trust-store password; excluded from toString
 */
@ConfigurationProperties("inertia.session.redis.tls")
public record InertiaRedisTlsProperties(
    @DefaultValue("false") boolean enabled, String trustStore, String trustStorePassword) {
  /**
   * Rejects incomplete TLS selections instead of silently using plaintext.
   *
   * @throws IllegalArgumentException if trust material is specified without TLS or a file
   */
  public InertiaRedisTlsProperties {
    if ((!enabled && (trustStore != null || trustStorePassword != null))
        || (trustStorePassword != null && trustStore == null)
        || (trustStore != null && trustStore.isBlank()))
      throw new IllegalArgumentException("Invalid Redis TLS trust configuration");
  }

  /**
   * Converts bound properties into explicit transport trust options.
   *
   * @return disabled, system-trust or PKCS12 trust-store options
   * @throws IllegalArgumentException if the trust-store file is unreadable
   */
  public RedisTlsOptions options() {
    if (!enabled) return RedisTlsOptions.disabled();
    if (trustStore == null) return RedisTlsOptions.systemTrust();
    return RedisTlsOptions.trustStore(
        Path.of(trustStore), trustStorePassword == null ? null : trustStorePassword.toCharArray());
  }

  /**
   * @return TLS activation with paths and credentials redacted
   */
  @Override
  public String toString() {
    return "InertiaRedisTlsProperties[enabled="
        + enabled
        + ", trust=<configured>, credentials=<redacted>]";
  }
}
