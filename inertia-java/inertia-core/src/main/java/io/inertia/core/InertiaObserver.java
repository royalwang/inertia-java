package io.inertia.core;

import java.util.*;

/**
 * Optional synchronous observation sink for render, provider, session, and protocol events.
 *
 * <p>Library events exclude headers, URLs, prop values, and exception text. Custom publishers must
 * apply the same discipline: the Event constructor does not redact arbitrary supplied strings.
 * Callbacks should return promptly. Library publication isolates RuntimeException from observers,
 * but does not suppress fatal Errors or provide asynchronous buffering.
 */
@FunctionalInterface
public interface InertiaObserver {
  /** Observer that discards all events without allocating external diagnostic resources. */
  InertiaObserver NOOP = event -> {};

  /** Bounded operation names used by events and metrics. */
  enum Operation {
    /** Prop planning and selected-provider resolution. */
    PROPS,
    /** Replacement of a definition by a higher-precedence layer. */
    PROP_OVERRIDE,
    /** Overall SSR attempt or CSR fallback selection. */
    SSR,
    /** HTTP renderer transport operation. */
    SSR_HTTP,
    /** Complete Java Page render operation. */
    RENDER,
    /** Reservation of one-time session data. */
    SESSION_BEGIN,
    /** Consumption of a reserved delivery. */
    SESSION_COMPLETE,
    /** Restoration of a reserved delivery. */
    SESSION_ABORT,
    /** Commit of pending redirect effects. */
    SESSION_MERGE,
    /** Pre-render asset-version conflict. */
    VERSION_CONFLICT,
    /** Adapter response preparation and transport reporting. */
    RESPONSE
  }

  /** Terminal operation classification. */
  enum Outcome {
    /** Operation completed successfully. */
    SUCCESS,
    /** Operation failed outside the more specific timeout/cancellation categories. */
    FAILURE,
    /** Operation exhausted its budget. */
    TIMEOUT,
    /** Operation was cancelled. */
    CANCELLED,
    /** SSR selected a client-side rendering fallback. */
    FALLBACK,
    /** Protocol asset versions conflicted. */
    CONFLICT
  }

  /** Bounded diagnostic reasons; no exception messages or remote bodies. */
  enum Reason {
    /** No failure or exceptional reason. */
    NONE,
    /** Unclassified operation failure. */
    ERROR,
    /** Invalid or conflicting prop definitions. */
    PROP_DEFINITION,
    /** A non-error prop definition replaced an earlier layer. */
    PROP_OVERRIDE,
    /** Application definitions replaced the generated errors prop. */
    ERRORS_OVERRIDE,
    /** A known timeout occurred. */
    TIMEOUT,
    /** Cancellation was observed. */
    CANCELLED,
    /** Executor or transport capacity was exhausted. */
    OVERLOADED,
    /** SSR was disabled or no gateway was configured. */
    DISABLED,
    /** Endpoint policy excluded the request or found no available renderer. */
    EXCLUDED_OR_UNAVAILABLE,
    /** Transport failure without a more specific reliable category. */
    TRANSPORT_OR_TIMEOUT,
    /** Renderer transport failed. */
    TRANSPORT,
    /** Renderer connection failed. */
    CONNECTION,
    /** Renderer response exceeded the byte limit. */
    RESPONSE_LIMIT,
    /** Renderer returned an unacceptable HTTP status. */
    HTTP_STATUS,
    /** Renderer returned its warming-up null result. */
    WARMING_UP,
    /** Renderer result violated the expected shape or content contract. */
    INVALID_RESPONSE,
    /** Renderer result could not be decoded as JSON. */
    INVALID_JSON,
    /** Renderer build identity differed from the Page version. */
    BUILD_MISMATCH,
    /** Renderer output used the wrong root. */
    ROOT_MISMATCH,
    /** Browser asset version differed from the current application version. */
    VERSION_MISMATCH,
    /** Unrecognized fallback reason supplied by an extension. */
    UNKNOWN
  }

  /** Bounded response shape for observations and timer tags. */
  enum ResponseKind {
    /** No HTTP response shape applies to the event. */
    NONE,
    /** HTML content response. */
    HTML,
    /** JSON content response. */
    JSON,
    /** Conventional Location redirect. */
    REDIRECT,
    /** Inertia full-navigation or fragment-redirect response. */
    LOCATION,
    /** Other response content or shape. */
    OTHER
  }

  /**
   * Immutable observation value with bounded category fields and caller-supplied diagnostic
   * identifiers.
   *
   * @param operation operation category
   * @param outcome terminal outcome category
   * @param reason bounded diagnostic reason
   * @param elapsedNanos duration clamped to zero when negative
   * @param status HTTP status, normalized to zero unless in the 100-599 range
   * @param response response shape
   * @param requestId non-null diagnostic request ID; publishers must supply safe server-generated
   *     text
   * @param component non-null registered component label or an empty/unregistered placeholder
   * @param endpointId non-null diagnostic endpoint label, never a URL or credentials
   */
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
    /**
     * Validates non-null fields and normalizes duration/status.
     *
     * <p>String fields are not redacted or syntax-validated here; custom publishers own that
     * policy.
     *
     * @throws NullPointerException if a category or diagnostic string is null
     */
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

  /**
   * Receives an event synchronously on the publishing thread.
   *
   * @param event already prepared diagnostic event; do not retain sensitive application state
   */
  void observe(Event event);

  /**
   * Creates an ordered observer fan-out with per-delegate RuntimeException isolation.
   *
   * @param observers non-null delegates, copied at construction; an empty array yields a no-op sink
   * @return sink publishing independently to every delegate in order
   * @throws NullPointerException if the array or a delegate is null
   */
  static InertiaObserver combine(InertiaObserver... observers) {
    var delegates = List.of(observers);
    return event -> delegates.forEach(observer -> Observations.publish(observer, event));
  }
}
