package io.inertia.core;

import static io.inertia.core.InertiaObserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared once-only span and bounded classification for adapters and core. */
public final class Observations {
  private Observations() {}

  public static String endpointId(String value) {
    if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}"))
      throw new IllegalArgumentException("Unsafe observation endpoint id");
    return value;
  }

  public static void publish(InertiaObserver observer, Event event) {
    try {
      observer.observe(event);
    } catch (RuntimeException ignored) {
      /* Diagnostics must not replace business outcomes. */
    }
  }

  public static Span start(
      InertiaObserver observer,
      Operation operation,
      InertiaRequest request,
      String component,
      String endpointId) {
    return new Span(
        observer, operation, request.requestId(), component == null ? "" : component, endpointId);
  }

  public static Reason failureReason(Throwable error) {
    var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (var current = error;
        current != null && visited.add(current);
        current = current.getCause()) {
      if (current instanceof CancellationException) return Reason.CANCELLED;
      if (current instanceof TimeoutException) return Reason.TIMEOUT;
      if (current instanceof RejectedExecutionException) return Reason.OVERLOADED;
    }
    return Reason.ERROR;
  }

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

  public static ResponseKind responseKind(HttpOutcome outcome) {
    if (outcome.header("X-Inertia-Location") != null
        || outcome.header("X-Inertia-Redirect") != null) return ResponseKind.LOCATION;
    if (outcome.header("Location") != null) return ResponseKind.REDIRECT;
    var contentType = Optional.ofNullable(outcome.header("Content-Type")).orElse("");
    if (contentType.startsWith("application/json")) return ResponseKind.JSON;
    if (contentType.startsWith("text/html")) return ResponseKind.HTML;
    return ResponseKind.OTHER;
  }

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

    public void success() {
      end(Outcome.SUCCESS, Reason.NONE, 0, ResponseKind.NONE);
    }

    public void success(HttpOutcome outcome) {
      end(Outcome.SUCCESS, Reason.NONE, outcome.status(), responseKind(outcome));
    }

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

    public void fallback(String reason) {
      end(Outcome.FALLBACK, fallbackReason(reason), 0, ResponseKind.NONE);
    }

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
