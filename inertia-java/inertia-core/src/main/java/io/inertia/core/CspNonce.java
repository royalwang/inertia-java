package io.inertia.core;

/** Nonces originate in trusted server request state, never client request headers. */
public final class CspNonce {
  private CspNonce() {}

  public static String require(String value) {
    if (value != null && !value.matches("[A-Za-z0-9+/_-]{16,256}={0,2}"))
      throw new IllegalArgumentException("Invalid CSP nonce");
    return value;
  }

  public static String attribute(String value) {
    return value == null ? "" : " nonce=\"" + require(value) + "\"";
  }
}
