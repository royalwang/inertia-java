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

  public InertiaContext(InertiaRequest request, SessionStore session, PageCodec codec) {
    this.request = request;
    this.session = session;
    this.codec = codec;
    pending = codec.object();
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

  public synchronized InertiaContext withErrors(Map<String, String> errors) {
    return withErrors("default", errors);
  }

  public synchronized InertiaContext withErrors(String bag, Map<String, String> errors) {
    if (state != State.CREATED)
      throw new IllegalStateException("Validation errors must be queued before rendering");
    pending.withObject("/" + ERRORS).set(bag, codec.value(errors));
    return this;
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

  public HttpOutcome backWithErrors(Map<String, String> errors) {
    withErrors(errors);
    return back();
  }

  public HttpOutcome location(String url) {
    return ProtocolPolicy.location(request, url);
  }

  synchronized ObjectNode begin() {
    if (state != State.CREATED) throw new IllegalStateException("Context already used");
    state = State.RESOLVING;
    delivery = session == null ? null : session.beginPageDelivery();
    return delivery == null ? codec.object() : delivery.data().deepCopy();
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
    if (delivery != null) session.completePageDelivery(delivery);
    pending.removeAll();
    state = State.COMMITTED;
  }

  /** Abort a timed-out transport/render and restore any reserved session snapshot. */
  public synchronized void abort() {
    failed();
  }

  synchronized void failed() {
    if (state == State.COMMITTED || state == State.FAILED) return;
    if (delivery != null) session.abortPageDelivery(delivery);
    state = State.FAILED;
  }

  public synchronized void commitRedirect() {
    if (state != State.CREATED) throw new IllegalStateException("Context already used");
    if (session == null && !pending.isEmpty())
      throw new IllegalStateException("Flash/errors across redirects require a session");
    if (session != null) session.merge(pending);
    pending.removeAll();
    state = State.COMMITTED;
  }

  private void writable() {
    if (state != State.CREATED && state != State.RESOLVING)
      throw new IllegalStateException("Request effects already closed");
  }
}
