package io.inertia.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Request-owned pending effects. Late writes and duplicate flash keys are rejected. */
public final class InertiaContext {
  public static final String FLASH = "inertia.flash_data";
  public static final String ERRORS = "inertia.errors";
  public static final String CLEAR = "inertia.clear_history";
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

  public InertiaContext(InertiaRequest request, SessionStore session, PageCodec codec) {
    this.request = request;
    this.session = session;
    this.codec = codec;
    pending = codec.object();
  }

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

  public InertiaRequest request() {
    return request;
  }

  public InertiaResponse render(String component, Props props) {
    return new InertiaResponse(component, props);
  }

  public synchronized InertiaContext share(String key, Object value) {
    if (state != State.CREATED)
      throw new IllegalStateException("Shared props must be defined before rendering");
    shared.put(key, value);
    return this;
  }

  public synchronized InertiaContext flash(String key, Object value) {
    writable();
    var flash = pending.withObject("/" + FLASH);
    if (flash.has(key))
      throw new IllegalStateException("Duplicate flash key within request: " + key);
    flash.set(key, codec.value(value));
    return this;
  }

  public synchronized InertiaContext withErrors(Map<String, ?> errors) {
    return withErrors("default", errors);
  }

  public synchronized InertiaContext withErrors(String bag, Map<String, ?> errors) {
    return withErrors(ErrorBags.empty().with(bag, ValidationErrors.from(errors)));
  }

  public synchronized InertiaContext withErrors(ValidationErrors errors) {
    return withErrors(ErrorBags.empty().with("default", errors));
  }

  public synchronized InertiaContext withErrors(ErrorBags errors) {
    if (state != State.CREATED)
      throw new IllegalStateException("Validation errors must be queued before rendering");
    pending.set(ERRORS, ErrorBags.fromJson(pending.get(ERRORS)).merge(errors).toJson());
    return this;
  }

  /** Request-local override. Redirects do not persist this setting into the next request. */
  public synchronized InertiaContext encryptHistory(boolean encrypt) {
    writable();
    encryptHistory = encrypt;
    return this;
  }

  synchronized Boolean encryptHistory() {
    return encryptHistory;
  }

  public synchronized InertiaContext clearHistory() {
    writable();
    pending.put(CLEAR, true);
    return this;
  }

  public synchronized InertiaContext preserveFragment() {
    writable();
    pending.put(FRAGMENT, true);
    return this;
  }

  public HttpOutcome back() {
    return ProtocolPolicy.redirect(request.safeBack());
  }

  public HttpOutcome backWithErrors(Map<String, ?> errors) {
    withErrors(errors);
    return back();
  }

  public HttpOutcome backWithErrors(ValidationErrors errors) {
    withErrors(errors);
    return back();
  }

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

  /** Abort a timed-out transport/render and restore any reserved session snapshot. */
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
