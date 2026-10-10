package io.inertia.spring;

import io.inertia.core.SessionStore;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Creates one request-owned store handle from trusted host session state. Applications own backend
 * connections and identity lifecycle. Do not select storage keys from request parameters/headers or
 * silently fall back after a storage failure. The adapter reuses this exact handle for typed
 * outcome advice; error Pages remain sessionless.
 */
@FunctionalInterface
public interface InertiaSessionStoreFactory {
  /**
   * Attaches the request to its trusted host session delivery domain.
   *
   * @param request current Servlet request
   * @return non-null store handle owned by this request
   */
  SessionStore create(HttpServletRequest request);
}
