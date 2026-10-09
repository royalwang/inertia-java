package io.inertia.example;

import io.inertia.core.*;
import io.inertia.spring.HttpSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/** Example-only recovery; a rejected write is never replayed or allowed through. */
final class BrowserCsrfFailureHandler implements AccessDeniedHandler {
  static final String MESSAGE = "Your security token changed. Review your form and submit again.";
  private final PageCodec codec;
  private final String namespace;

  BrowserCsrfFailureHandler(PageCodec codec, String namespace) {
    this.codec = codec;
    this.namespace = SessionStore.requireNamespace(namespace);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
      throws IOException {
    if (!(failure instanceof CsrfException)
        || !"POST".equals(request.getMethod())
        || !((request.getContextPath() + "/users").equals(request.getRequestURI()))
        || !"true".equals(request.getHeader("X-Inertia"))) {
      response.sendError(403);
      return;
    }
    try {
      // The redirect target is fixed. Incoming Referer, URL, tokens and form data are not copied.
      var context =
          new InertiaContext(
              new InertiaRequest("POST", URI.create("http://localhost/users"), Map.of()),
              new HttpSessionStore(request.getSession(true), namespace),
              codec);
      context.withErrors(Map.of("_csrf", MESSAGE));
      context.commitRedirect();
    } catch (RuntimeException storageFailure) {
      response.sendError(500);
      return;
    }
    response.setHeader("Cache-Control", "no-store");
    response.addHeader("Vary", "X-Inertia");
    response.setHeader("Location", request.getContextPath() + "/users");
    response.setStatus(303);
  }
}
