package io.inertia.spring;

import io.inertia.core.InertiaRequest;
import io.inertia.core.InertiaResponse;

/** Application-owned safe error page. Exception details are never supplied as page props. */
@FunctionalInterface
public interface InertiaErrorPage {
  /**
   * Creates a safe, registered error Page without receiving the failed exception as input.
   *
   * <p>The resolver forces the chosen 4xx/5xx status and private/no-store caching. Rendering uses a
   * fresh sessionless context so restored one-time data remains available to a later successful
   * Page. Required SSR failures disable SSR for this fallback. A factory/render failure falls back
   * once to plain text.
   *
   * @param request captured metadata; choose only client-safe data for the error Page
   * @param status classified error status from 400 through 599
   * @return non-null request-owned response definition for a registered error component
   */
  InertiaResponse create(InertiaRequest request, int status);
}
