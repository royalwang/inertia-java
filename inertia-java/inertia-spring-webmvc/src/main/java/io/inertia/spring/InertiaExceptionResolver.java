package io.inertia.spring;

import io.inertia.core.*;
import jakarta.servlet.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;

/** One error-page attempt after application advice, then safe plain text. Never handles REST. */
final class InertiaExceptionResolver implements HandlerExceptionResolver {
  private static final String ATTEMPT = InertiaExceptionResolver.class.getName() + ".attempt";
  private static final Log log = LogFactory.getLog(InertiaExceptionResolver.class);
  private final ResponseRenderer renderer;
  private final Duration deadline;
  private final InertiaErrorPage errorPage;

  InertiaExceptionResolver(
      ResponseRenderer renderer, Duration deadline, InertiaErrorPage errorPage) {
    this.renderer = renderer;
    this.deadline = deadline;
    this.errorPage = errorPage;
  }

  @Override
  public ModelAndView resolveException(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
    if (!(handler instanceof HandlerMethod method)
        || !InertiaHandlerValidator.supports(method)
        || response.isCommitted()) return null;
    try {
      InertiaMvcConfigurer.abort(request);
    } catch (RuntimeException cleanupFailure) {
      log.warn("Inertia session cleanup failed: type=" + cleanupFailure.getClass().getName());
      return plain(request, response, 500);
    }
    int status = status(failure);
    log.warn("Inertia page failed: status=" + status + ", type=" + failure.getClass().getName());
    if (request.getAttribute(ATTEMPT) != null) return plain(request, response, 500);
    request.setAttribute(ATTEMPT, Boolean.TRUE);
    if (errorPage == null) return plain(request, response, status);
    try {
      var snapshot = InertiaMvcConfigurer.snapshot(request);
      var page =
          Objects.requireNonNull(
              errorPage.create(snapshot, status), "Error page factory returned null");
      if (requiredSsrFailure(failure)) page.withoutSsr();
      page.status(status).withHeader("Cache-Control", "private, no-store");
      // A failed ordinary page's reservation is restored, not consumed by its error page.
      var context = new InertiaContext(snapshot, null, new PageCodec());
      var pending = renderer.render(context, page).toCompletableFuture();
      HttpOutcome outcome;
      try {
        outcome = pending.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
      } catch (TimeoutException | InterruptedException error) {
        try {
          context.abort();
        } catch (RuntimeException | Error cleanup) {
          error.addSuppressed(cleanup);
        } finally {
          pending.cancel(true);
        }
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        throw error;
      }
      clean(response);
      InertiaMvcConfigurer.writeObserved(
          request, response, outcome, renderer.observer(), page.component());
      return new ModelAndView();
    } catch (Exception error) {
      log.warn("Inertia error page failed: type=" + error.getClass().getName());
      return response.isCommitted() ? null : plain(request, response, 500);
    }
  }

  private static boolean requiredSsrFailure(Throwable failure) {
    var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (var current = failure;
        current != null && visited.add(current);
        current = current.getCause()) if (current instanceof SsrRequiredException) return true;
    return false;
  }

  private static int status(Throwable failure) {
    var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (Throwable current = failure;
        current != null && visited.add(current);
        current = current.getCause()) {
      if (current instanceof SsrRequiredException) return 503;
      if (current instanceof org.springframework.beans.ConversionNotSupportedException) return 500;
      if (current instanceof org.springframework.beans.TypeMismatchException
          || current instanceof org.springframework.http.converter.HttpMessageNotReadableException
          || current instanceof org.springframework.validation.BindException) return 400;
      if (current instanceof ErrorResponse error) return safeStatus(error.getStatusCode().value());
      var annotation =
          AnnotatedElementUtils.findMergedAnnotation(current.getClass(), ResponseStatus.class);
      if (annotation != null) return safeStatus(annotation.code().value());
    }
    return 500;
  }

  private static int safeStatus(int status) {
    return status >= 400 && status <= 599 ? status : 500;
  }

  private static void clean(HttpServletResponse response) {
    response.resetBuffer();
    for (String header :
        List.of(
            "X-Inertia",
            "X-Inertia-Version",
            "X-Inertia-Location",
            "X-Inertia-Redirect",
            "Content-Type",
            "Content-Length")) response.setHeader(header, null);
  }

  private ModelAndView plain(HttpServletRequest request, HttpServletResponse response, int status) {
    try {
      clean(response);
      var outcome =
          new HttpOutcome(
                  status, Map.of(), status == 500 ? "Internal Server Error" : "Request failed")
              .withHeader("Content-Type", "text/plain; charset=utf-8")
              .withHeader("Cache-Control", "private, no-store")
              .vary();
      InertiaMvcConfigurer.writeObserved(request, response, outcome, renderer.observer(), "");
      return new ModelAndView();
    } catch (Exception writeFailure) {
      return null;
    }
  }
}
