package io.inertia.core;

import static io.inertia.core.InertiaObserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared once-only span and bounded classification for adapters and core. */
public final class Observations {
  private Observations() {}

  /**
   * Validates a bounded diagnostic endpoint label, without selecting a network destination.
   *
   * @param value 1-64 characters starting with an ASCII letter and continuing with letters, digits,
   *     underscore, dot, or hyphen
   * @return unchanged valid label
   * @throws IllegalArgumentException if the value is null or unsafe
   */
  public static String endpointId(String value) {
    if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}"))
      throw new IllegalArgumentException("Unsafe observation endpoint id");
    return value;
  }

  /**
   * Publishes synchronously while suppressing an observer's RuntimeException.
   *
   * <p>Fatal Errors still propagate. No executor, queue, timeout, or additional redaction is
   * applied.
   *
   * @param observer event sink
   * @param event prepared diagnostic event
   */
  public static void publish(InertiaObserver observer, Event event) {
    try {
      observer.observe(event);
    } catch (RuntimeException ignored) {
      /* Diagnostics must not replace business outcomes. */
    }
  }

  /**
   * Starts a monotonic elapsed-time span carrying safe caller-selected labels.
   *
   * @param observer non-null event sink
   * @param operation bounded operation category
   * @param request captured server request metadata supplying the request ID
   * @param component safe component label; null is normalized to an empty string
   * @param endpointId safe diagnostic label, already validated by the caller if necessary
   * @return span that publishes only its first terminal event
   * @throws NullPointerException if observer or request is null
   */
  public static Span start(
      InertiaObserver observer,
      Operation operation,
      InertiaRequest request,
      String component,
      String endpointId) {
    return new Span(
        observer, operation, request.requestId(), component == null ? "" : component, endpointId);
  }

  /**
   * Finds a bounded known failure category while walking the cause chain without looping.
   *
   * @param error failure, or null for an unclassified error
   * @return required-SSR reason, cancellation, definition, timeout, overload, or generic ERROR
   */
  public static Reason failureReason(Throwable error) {
    var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (var current = error;
        current != null && visited.add(current);
        current = current.getCause()) {
      if (current instanceof SsrRequiredException required) return required.reason();
      if (current instanceof CancellationException) return Reason.CANCELLED;
      if (current instanceof PropDefinitionException) return Reason.PROP_DEFINITION;
      if (current instanceof TimeoutException) return Reason.TIMEOUT;
      if (current instanceof RejectedExecutionException) return Reason.OVERLOADED;
    }
    return Reason.ERROR;
  }

  /**
   * Maps a gateway fallback token to a bounded reason.
   *
   * @param value non-null fallback token
   * @return recognized reason, or UNKNOWN for an unrecognized token
   * @throws NullPointerException if value is null
   */
  public static Reason fallbackReason(String value) {
    return switch (value) {
      case "disabled" -> Reason.DISABLED;
      case "excluded-or-unavailable" -> Reason.EXCLUDED_OR_UNAVAILABLE;
      case "overloaded" -> Reason.OVERLOADED;
      case "transport-or-timeout" -> Reason.TRANSPORT_OR_TIMEOUT;
      case "http-status" -> Reason.HTTP_STATUS;
      case "warming-up" -> Reason.WARMING_UP;
      case "invalid-response" -> Reason.INVALID_RESPONSE;
      case "invalid-json" -> Reason.INVALID_JSON;
      case "build-mismatch" -> Reason.BUILD_MISMATCH;
      case "root-mismatch" -> Reason.ROOT_MISMATCH;
      default -> Reason.UNKNOWN;
    };
  }

  /**
   * Classifies an outcome by navigation headers first, then Content-Type.
   *
   * @param outcome prepared response
   * @return LOCATION, REDIRECT, JSON, HTML, or OTHER according to its headers
   */
  public static ResponseKind responseKind(HttpOutcome outcome) {
    if (outcome.header("X-Inertia-Location") != null
        || outcome.header("X-Inertia-Redirect") != null) return ResponseKind.LOCATION;
    if (outcome.header("Location") != null) return ResponseKind.REDIRECT;
    var contentType = Optional.ofNullable(outcome.header("Content-Type")).orElse("");
    if (contentType.startsWith("application/json")) return ResponseKind.JSON;
    if (contentType.startsWith("text/html")) return ResponseKind.HTML;
    return ResponseKind.OTHER;
  }

  /**
   * One-shot observation span with a monotonic start time and atomic terminal publication.
   *
   * <p>Only the first terminal call publishes. This guard does not cancel or complete the business
   * operation being observed.
   */
  public static final class Span {
    private final InertiaObserver observer;
    private final Operation operation;
    private final String requestId, component, endpointId;
    private final long started = System.nanoTime();
    private final AtomicBoolean finished = new AtomicBoolean();

    private Span(
        InertiaObserver observer,
        Operation operation,
        String requestId,
        String component,
        String endpointId) {
      this.observer = Objects.requireNonNull(observer);
      this.operation = operation;
      this.requestId = requestId;
      this.component = component;
      this.endpointId = endpointId;
    }

    /** Publishes success without an HTTP status or response shape, unless already finished. */
    public void success() {
      end(Outcome.SUCCESS, Reason.NONE, 0, ResponseKind.NONE);
    }

    /**
     * Publishes success with the prepared response's status and inferred shape.
     *
     * @param outcome prepared response
     */
    public void success(HttpOutcome outcome) {
      end(Outcome.SUCCESS, Reason.NONE, outcome.status(), responseKind(outcome));
    }

    /**
     * Publishes a categorized failure, timeout, or cancellation unless already finished.
     *
     * @param error failure whose cause chain supplies the bounded reason
     */
    public void failure(Throwable error) {
      var reason = failureReason(error);
      end(
          reason == Reason.TIMEOUT
              ? Outcome.TIMEOUT
              : reason == Reason.CANCELLED ? Outcome.CANCELLED : Outcome.FAILURE,
          reason,
          0,
          ResponseKind.NONE);
    }

    /**
     * Publishes an SSR fallback unless already finished.
     *
     * @param reason non-null gateway fallback token
     */
    public void fallback(String reason) {
      end(Outcome.FALLBACK, fallbackReason(reason), 0, ResponseKind.NONE);
    }

    /**
     * Atomically publishes the first terminal event for this span.
     *
     * @param outcome terminal outcome category
     * @param reason bounded diagnostic reason
     * @param status HTTP status, or zero when not applicable
     * @param response response shape, or NONE when not applicable
     */
    public void end(Outcome outcome, Reason reason, int status, ResponseKind response) {
      if (finished.compareAndSet(false, true))
        publish(
            observer,
            new Event(
                operation,
                outcome,
                reason,
                System.nanoTime() - started,
                status,
                response,
                requestId,
                component,
                endpointId));
    }
  }
}
