package io.inertia.core;

import java.util.*;

/** Optional diagnostics. Events contain no request headers, URL, props or exception text. */
@FunctionalInterface
public interface InertiaObserver {
  InertiaObserver NOOP = event -> {};

  enum Operation {
    PROPS,
    SSR,
    SSR_HTTP,
    RENDER,
    SESSION_BEGIN,
    SESSION_COMPLETE,
    SESSION_ABORT,
    SESSION_MERGE,
    VERSION_CONFLICT,
    RESPONSE
  }

  enum Outcome {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    CANCELLED,
    FALLBACK,
    CONFLICT
  }

  enum Reason {
    NONE,
    ERROR,
    TIMEOUT,
    CANCELLED,
    OVERLOADED,
    DISABLED,
    EXCLUDED_OR_UNAVAILABLE,
    TRANSPORT_OR_TIMEOUT,
    TRANSPORT,
    CONNECTION,
    RESPONSE_LIMIT,
    HTTP_STATUS,
    WARMING_UP,
    INVALID_RESPONSE,
    INVALID_JSON,
    BUILD_MISMATCH,
    ROOT_MISMATCH,
    VERSION_MISMATCH,
    UNKNOWN
  }

  enum ResponseKind {
    NONE,
    HTML,
    JSON,
    REDIRECT,
    LOCATION,
    OTHER
  }

  record Event(
      Operation operation,
      Outcome outcome,
      Reason reason,
      long elapsedNanos,
      int status,
      ResponseKind response,
      String requestId,
      String component,
      String endpointId) {
    public Event {
      Objects.requireNonNull(operation);
      Objects.requireNonNull(outcome);
      Objects.requireNonNull(reason);
      Objects.requireNonNull(response);
      elapsedNanos = Math.max(0, elapsedNanos);
      status = status >= 100 && status <= 599 ? status : 0;
      Objects.requireNonNull(requestId);
      Objects.requireNonNull(component);
      Objects.requireNonNull(endpointId);
    }
  }

  void observe(Event event);

  static InertiaObserver combine(InertiaObserver... observers) {
    var delegates = List.of(observers);
    return event -> delegates.forEach(observer -> Observations.publish(observer, event));
  }
}
