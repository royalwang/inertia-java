package io.inertia.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/**
 * Request-owned render definitions and pending session effects; never reuse across requests.
 *
 * <p>Sharing and validation errors must be defined before resolution begins. Flash and history
 * effects remain writable while props resolve, then close at preparation. Rendering reserves
 * one-time session data and either completes its delivery or attempts restoration on failure.
 * Completion precedes HTTP writing: a later transport failure cannot roll back consumed flash.
 */
public final class InertiaContext {
  /** Storage key for pending flash data within the adapter session namespace. */
  public static final String FLASH = "inertia.flash_data";

  /** Storage key for validation error bags within the adapter session namespace. */
  public static final String ERRORS = "inertia.errors";

  /** Storage key for the pending clear-history effect. */
  public static final String CLEAR = "inertia.clear_history";

  /** Storage key for the pending preserve-fragment effect. */
  public static final String FRAGMENT = "inertia.preserve_fragment";

  private enum State {
    CREATED,
    RESOLVING,
    PREPARED,
    COMMITTED,
    FAILED
  }

  private State state = State.CREATED;
  private final InertiaRequest request;
  private final SessionStore session;
  private final PageCodec codec;
  private final Props.Builder shared = Props.builder();
  private final ObjectNode pending;
  private SessionStore.Delivery delivery;
  private Boolean encryptHistory;
  private InertiaObserver observer = InertiaObserver.NOOP;
  private String component = "";

  /**
   * Creates unused request state with no-op observation.
   *
   * @param request request metadata captured by the adapter
   * @param session request's session store, or null when session effects are unavailable
   * @param codec Page JSON codec for pending effects
   */
  public InertiaContext(InertiaRequest request, SessionStore session, PageCodec codec) {
    this.request = request;
    this.session = session;
    this.codec = codec;
    pending = codec.object();
  }

  /**
   * Creates unused request state with explicit observation.
   *
   * @param request request metadata captured by the adapter
   * @param session request's session store, or null when session effects are unavailable
   * @param codec Page JSON codec for pending effects
   * @param observer non-null operation observer
   * @throws NullPointerException if observer is null
   */
  public InertiaContext(
      InertiaRequest request, SessionStore session, PageCodec codec, InertiaObserver observer) {
    this(request, session, codec);
    this.observer = Objects.requireNonNull(observer);
  }

  synchronized void observer(InertiaObserver observer, String component) {
    if (state != State.CREATED) throw new IllegalStateException("Context already used");
    this.observer = Objects.requireNonNull(observer);
    this.component = component;
  }

  private <T> T observe(
      InertiaObserver.Operation operation, java.util.function.Supplier<T> action) {
    var span = Observations.start(observer, operation, request, component, "none");
    try {
      T value = action.get();
      span.success();
      return value;
    } catch (RuntimeException | Error failure) {
      span.failure(failure);
      throw failure;
    }
  }

  /**
   * Returns the request captured for this context.
   *
   * @return request metadata
   */
  public InertiaRequest request() {
    return request;
  }

  /**
   * Creates a response definition for the renderer; this call does not start resolution or commit.
   *
   * @param component configured component name
   * @param props Page prop definitions
   * @return response definition for the framework adapter
   */
  public InertiaResponse render(String component, Props props) {
    return new InertiaResponse(component, props);
  }

  /**
   * Adds a request-local shared prop before rendering begins.
   *
   * @param key prop name or valid dot-separated nested path
   * @param value literal value or Prop definition
   * @return this context
   * @throws IllegalStateException if resolution has started
   * @throws PropDefinitionException if the path is invalid
   */
  public synchronized InertiaContext share(String key, Object value) {
    if (state != State.CREATED)
      throw new IllegalStateException("Shared props must be defined before rendering");
    shared.put(key, value);
    return this;
  }

  /**
   * Queues one flash value while the context is unused or resolving props.
   *
   * @param key flash key, unique within this request
   * @param value JSON-convertible value
   * @return this context
   * @throws IllegalStateException if the key is duplicated or effects are already closed
   * @throws IllegalArgumentException if JSON conversion fails
   */
  public synchronized InertiaContext flash(String key, Object value) {
    writable();
    var flash = pending.withObject("/" + FLASH);
    if (flash.has(key))
      throw new IllegalStateException("Duplicate flash key within request: " + key);
    flash.set(key, codec.value(value));
    return this;
  }

  /**
   * Queues validation messages in the default bag before rendering begins.
   *
   * @param errors field names mapped to a message or list of messages
   * @return this context
   * @throws IllegalStateException if resolution has started
   * @throws IllegalArgumentException if error input is invalid
   */
  public synchronized InertiaContext withErrors(Map<String, ?> errors) {
    return withErrors("default", errors);
  }

  /**
   * Queues validation messages in a named bag before rendering begins.
   *
   * @param bag non-null error-bag name
   * @param errors field names mapped to a message or list of messages
   * @return this context
   * @throws IllegalStateException if resolution has started
   * @throws IllegalArgumentException if error input is invalid
   */
  public synchronized InertiaContext withErrors(String bag, Map<String, ?> errors) {
    return withErrors(ErrorBags.empty().with(bag, ValidationErrors.from(errors)));
  }

  /**
   * Queues validated messages in the default bag before rendering begins.
   *
   * @param errors validated field messages
   * @return this context
   * @throws IllegalStateException if resolution has started
   */
  public synchronized InertiaContext withErrors(ValidationErrors errors) {
    return withErrors(ErrorBags.empty().with("default", errors));
  }

  /**
   * Merges error bags into pending request state before rendering begins.
   *
   * @param errors validated bags to merge with already queued bags
   * @return this context
   * @throws IllegalStateException if resolution has started
   */
  public synchronized InertiaContext withErrors(ErrorBags errors) {
    if (state != State.CREATED)
      throw new IllegalStateException("Validation errors must be queued before rendering");
    pending.set(ERRORS, ErrorBags.fromJson(pending.get(ERRORS)).merge(errors).toJson());
    return this;
  }

  /**
   * Overrides history encryption for this Page only; redirects do not persist the override.
   *
   * @param encrypt whether the client should encrypt this Page's history state
   * @return this context
   * @throws IllegalStateException if effects are already closed
   */
  public synchronized InertiaContext encryptHistory(boolean encrypt) {
    writable();
    encryptHistory = encrypt;
    return this;
  }

  synchronized Boolean encryptHistory() {
    return encryptHistory;
  }

  /**
   * Queues the client clear-history flag for this Page or the next Page after redirect.
   *
   * @return this context
   * @throws IllegalStateException if effects are already closed
   */
  public synchronized InertiaContext clearHistory() {
    writable();
    pending.put(CLEAR, true);
    return this;
  }

  /**
   * Queues preservation of the client URL fragment for this Page or after redirect.
   *
   * @return this context
   * @throws IllegalStateException if effects are already closed
   */
  public synchronized InertiaContext preserveFragment() {
    writable();
    pending.put(FRAGMENT, true);
    return this;
  }

  /**
   * Creates a redirect to the request's validated same-origin back target.
   *
   * <p>The adapter must commit pending redirect effects separately through {@link
   * #commitRedirect()}.
   *
   * @return protocol redirect outcome using the safe referer or fallback path
   */
  public HttpOutcome back() {
    return ProtocolPolicy.redirect(request.safeBack());
  }

  /**
   * Queues default-bag validation messages and creates a safe back redirect.
   *
   * @param errors field message input
   * @return back redirect outcome; the adapter still commits pending effects
   * @throws IllegalStateException if resolution has started
   * @throws IllegalArgumentException if error input is invalid
   */
  public HttpOutcome backWithErrors(Map<String, ?> errors) {
    withErrors(errors);
    return back();
  }

  /**
   * Queues validated default-bag messages and creates a safe back redirect.
   *
   * @param errors validated field messages
   * @return back redirect outcome; the adapter still commits pending effects
   * @throws IllegalStateException if resolution has started
   */
  public HttpOutcome backWithErrors(ValidationErrors errors) {
    withErrors(errors);
    return back();
  }

  /**
   * Creates a protocol location outcome for a full browser navigation.
   *
   * @param url application-approved navigation destination; only header characters are validated
   * @return Inertia location response or ordinary redirect according to the request
   * @throws IllegalArgumentException if the destination contains invalid header characters
   */
  public HttpOutcome location(String url) {
    return ProtocolPolicy.location(request, url);
  }

  synchronized ObjectNode begin() {
    if (state != State.CREATED) throw new IllegalStateException("Context already used");
    state = State.RESOLVING;
    delivery =
        session == null
            ? null
            : observe(
                InertiaObserver.Operation.SESSION_BEGIN,
                () ->
                    Objects.requireNonNull(
                        session.beginPageDelivery(), "Missing session delivery"));
    return delivery == null ? codec.object() : delivery.data();
  }

  synchronized Props shared() {
    return shared.build();
  }

  synchronized ObjectNode pending() {
    return pending.deepCopy();
  }

  synchronized void prepared() {
    if (state != State.RESOLVING) throw new IllegalStateException("Invalid prepare state");
    state = State.PREPARED;
  }

  synchronized void complete() {
    if (state != State.PREPARED) throw new IllegalStateException("Invalid completion state");
    try {
      if (delivery != null)
        observe(
            InertiaObserver.Operation.SESSION_COMPLETE,
            () -> {
              session.completePageDelivery(delivery);
              return null;
            });
    } catch (RuntimeException | Error failure) {
      fail(failure);
      throw failure;
    }
    pending.removeAll();
    state = State.COMMITTED;
  }

  /**
   * Aborts an unfinished render and attempts to restore any reserved session snapshot once.
   *
   * <p>Repeated calls after failure or commitment do nothing. A committed Page's flash cannot be
   * restored by aborting a later HTTP write. Storage failures propagate; this method does not retry
   * unknown transaction outcomes.
   */
  public synchronized void abort() {
    failed();
  }

  synchronized void failed() {
    if (state == State.COMMITTED || state == State.FAILED) return;
    state = State.FAILED;
    if (delivery != null)
      observe(
          InertiaObserver.Operation.SESSION_ABORT,
          () -> {
            session.abortPageDelivery(delivery);
            return null;
          });
  }

  synchronized void fail(Throwable failure) {
    try {
      failed();
    } catch (Throwable cleanup) {
      Throwable primary = failure;
      var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
      while ((primary instanceof java.util.concurrent.CompletionException
              || primary instanceof java.util.concurrent.ExecutionException)
          && visited.add(primary)
          && primary.getCause() != null
          && primary.getCause() != primary) primary = primary.getCause();
      if (cleanup != primary) primary.addSuppressed(cleanup);
    }
  }

  /**
   * Atomically merges pending redirect effects into the session and closes this context.
   *
   * <p>Call only for an unused context before writing the redirect. Storage failure fails the
   * context and propagates. No automatic retry is performed. The history-encryption override is
   * request-local and is not part of the persisted redirect effects.
   *
   * @throws IllegalStateException if the context is used, or session effects exist without a store
   */
  public synchronized void commitRedirect() {
    if (state != State.CREATED) throw new IllegalStateException("Context already used");
    try {
      if (session == null && !pending.isEmpty())
        throw new IllegalStateException("Flash/errors across redirects require a session");
      if (session != null)
        observe(
            InertiaObserver.Operation.SESSION_MERGE,
            () -> {
              session.merge(pending);
              return null;
            });
    } catch (RuntimeException | Error failure) {
      fail(failure);
      throw failure;
    }
    pending.removeAll();
    state = State.COMMITTED;
  }

  private void writable() {
    if (state != State.CREATED && state != State.RESOLVING)
      throw new IllegalStateException("Request effects already closed");
  }
}
