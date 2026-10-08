package io.inertia.spring;

import io.inertia.core.InertiaRequest;
import io.inertia.core.InertiaResponse;

/** Application-owned safe error page. Exception details are never supplied as page props. */
@FunctionalInterface
public interface InertiaErrorPage {
  InertiaResponse create(InertiaRequest request, int status);
}
