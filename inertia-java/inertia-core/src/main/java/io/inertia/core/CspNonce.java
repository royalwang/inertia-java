package io.inertia.core;

/** Nonces originate in trusted server request state, never client request headers. */
public final class CspNonce {
  private CspNonce() {}

  /**
   * Validates a trusted server nonce without generating or reading it from the request.
   *
   * @param value 16-256 base64/base64url characters with up to two trailing padding signs, or null
   * @return unchanged nonce, including null
   * @throws IllegalArgumentException if the nonce contains disallowed characters or length
   */
  public static String require(String value) {
    if (value != null && !value.matches("[A-Za-z0-9+/_-]{16,256}={0,2}"))
      throw new IllegalArgumentException("Invalid CSP nonce");
    return value;
  }

  /**
   * Formats a validated optional nonce as an HTML attribute.
   *
   * @param value trusted server nonce, or null
   * @return leading-space nonce attribute, or an empty string when absent
   * @throws IllegalArgumentException if the nonce is invalid
   */
  public static String attribute(String value) {
    return value == null ? "" : " nonce=\"" + require(value) + "\"";
  }
}
