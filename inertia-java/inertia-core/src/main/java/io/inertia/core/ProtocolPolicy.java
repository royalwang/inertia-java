package io.inertia.core;

import java.util.*;

public final class ProtocolPolicy {
  private ProtocolPolicy() {}

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

  public static HttpOutcome redirect(String url) {
    return HttpOutcome.empty(302).withHeader("Location", url);
  }

  public static HttpOutcome location(InertiaRequest request, String url) {
    return request.isInertia()
        ? HttpOutcome.empty(409).withHeader("X-Inertia-Location", url).vary()
        : redirect(url).vary();
  }

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
