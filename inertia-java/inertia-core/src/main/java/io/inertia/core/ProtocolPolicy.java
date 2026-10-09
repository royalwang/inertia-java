package io.inertia.core;

import java.util.*;

/**
 * Framework-independent Inertia request/response protocol transitions.
 *
 * <p>Adapters apply the pre-policy before rendering and the post-policy before transport writes.
 * Redirect/location helpers validate header syntax through {@link HttpOutcome}; destinations are
 * application-controlled and are not independently authorized here.
 */
public final class ProtocolPolicy {
  private ProtocolPolicy() {}

  /**
   * Detects an asset-version conflict on an Inertia GET request.
   *
   * @param request captured request metadata
   * @param version current application asset version
   * @return location response carrying the version on conflict, otherwise empty
   */
  public static Optional<HttpOutcome> before(InertiaRequest request, String version) {
    if (!request.isInertia()
        || !request.method().equals("GET")
        || Objects.equals(
            Optional.ofNullable(request.header("x-inertia-version")).orElse(""), version))
      return Optional.empty();
    return Optional.of(
        location(request, request.fullUrl().toASCIIString())
            .withHeader("X-Inertia-Version", version));
  }

  /**
   * Creates a 302 redirect with the Inertia Vary policy.
   *
   * @param url application-approved destination
   * @return prepared redirect response
   * @throws IllegalArgumentException if the value contains CR or LF
   */
  public static HttpOutcome redirect(String url) {
    return HttpOutcome.empty(302).withHeader("Location", url).vary();
  }

  /**
   * Creates a full-document location response for Inertia visits, or a conventional redirect.
   *
   * @param request captured request metadata
   * @param url application-approved destination
   * @return 409 with X-Inertia-Location for Inertia, otherwise 302 with Location
   * @throws IllegalArgumentException if the value contains CR or LF
   */
  public static HttpOutcome location(InertiaRequest request, String url) {
    return request.isInertia()
        ? HttpOutcome.empty(409).withHeader("X-Inertia-Location", url).vary()
        : redirect(url).vary();
  }

  /**
   * Applies response normalization before the adapter writes bytes.
   *
   * <p>Ensures Vary for all requests. Inertia empty successful responses redirect back;
   * PUT/PATCH/DELETE 302 redirects become 303. Recognized redirect statuses containing fragments
   * use X-Inertia-Redirect unless the request is a prefetch. Application-owned status and headers
   * are otherwise preserved.
   *
   * @param request captured request metadata
   * @param response prepared outcome
   * @return normalized outcome
   */
  public static HttpOutcome after(InertiaRequest request, HttpOutcome response) {
    response = response.vary();
    if (!request.isInertia()) return response;
    if (response.status() == 200 && response.body().isEmpty())
      response = redirect(request.safeBack()).vary();
    if (response.status() == 302 && Set.of("PUT", "PATCH", "DELETE").contains(request.method()))
      response = response.withStatus(303);
    String location = response.header("location");
    if (Set.of(201, 301, 302, 303, 307, 308).contains(response.status())
        && location != null
        && location.contains("#")
        && !request.isPrefetch())
      return HttpOutcome.empty(409).withHeader("X-Inertia-Redirect", location).vary();
    return response;
  }
}
