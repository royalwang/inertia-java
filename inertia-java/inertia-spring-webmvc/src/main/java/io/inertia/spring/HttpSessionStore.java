package io.inertia.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.inertia.core.*;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Request adapter for single-node, namespaced {@link MemorySessionStore} state in an HttpSession.
 *
 * <p>Operations synchronize on Spring's session mutex and check the exact attached state before and
 * after access. Invalidation, attribute removal, or replacement fails rather than copying old
 * reservations into a new session. There is no automatic retry for an unknown storage outcome. This
 * adapter does not supply distributed transactions or qualify session replication behavior.
 */
public final class HttpSessionStore implements SessionStore {
  private static final String KEY = "io.inertia.session.state";
  private final HttpSession session;
  private final Object mutex;
  private final String attribute;
  private final MemorySessionStore state;

  /**
   * Attaches to or creates state in the default session namespace.
   *
   * @param session existing valid Servlet session
   * @throws NullPointerException if session is null
   * @throws IllegalStateException if the session is invalid or its state attribute is incompatible
   */
  public HttpSessionStore(HttpSession session) {
    this(session, DEFAULT_NAMESPACE);
  }

  /**
   * Attaches to the exact namespace state while holding the Spring session mutex.
   *
   * @param session existing valid Servlet session
   * @param namespace 1-64 character namespace accepted by {@link
   *     SessionStore#requireNamespace(String)}
   * @throws NullPointerException if session is null
   * @throws IllegalArgumentException if namespace is invalid
   * @throws IllegalStateException if the session is invalid, detached, or contains incompatible
   *     state
   */
  public HttpSessionStore(HttpSession session, String namespace) {
    this.session = Objects.requireNonNull(session);
    SessionStore.requireNamespace(namespace);
    attribute = KEY + (namespace.equals(DEFAULT_NAMESPACE) ? "" : "." + namespace);
    mutex = org.springframework.web.util.WebUtils.getSessionMutex(session);
    synchronized (mutex) {
      Object value = session.getAttribute(attribute);
      if (value == null) {
        value = new MemorySessionStore();
        session.setAttribute(attribute, value);
      }
      if (!(value instanceof MemorySessionStore memory))
        throw new IllegalStateException("Incompatible Inertia session state");
      state = memory;
      attached();
    }
  }

  private void attached() {
    if (session.getAttribute(attribute) != state)
      throw new IllegalStateException("Inertia session state was detached");
  }

  private <T> T active(Supplier<T> operation) {
    synchronized (mutex) {
      attached();
      T value = operation.get();
      attached();
      return value;
    }
  }

  /**
   * Reads available one-time data from the still-attached session state.
   *
   * @param key storage key
   * @return defensive copy, or null when absent
   * @throws IllegalStateException if the session is invalidated or the state is detached
   */
  public JsonNode get(String key) {
    return active(() -> state.get(key));
  }

  /**
   * Copies a value into available state under the session mutex.
   *
   * @param key storage key
   * @param value non-null JSON node; a JSON null node represents a stored null
   * @throws NullPointerException if value is null
   * @throws IllegalStateException if the session is invalidated or the state is detached
   */
  public void put(String key, JsonNode value) {
    active(
        () -> {
          state.put(key, value);
          return null;
        });
  }

  /**
   * Removes one available value without consuming reserved delivery data.
   *
   * @param key storage key
   * @return removed node, or null when absent
   * @throws IllegalStateException if the session is invalidated or the state is detached
   */
  public JsonNode pull(String key) {
    return active(() -> state.pull(key));
  }

  /**
   * Reserves available data in the attached namespace under one delivery token.
   *
   * @return token and defensive snapshot
   * @throws IllegalStateException if the session is invalidated or the state is detached
   */
  public Delivery beginPageDelivery() {
    return active(state::beginPageDelivery);
  }

  /**
   * Consumes one reserved token, leaving later writes available.
   *
   * @param delivery previously reserved delivery in this state
   * @throws IllegalStateException if token is consumed/unknown, session invalidated, or state
   *     detached
   */
  public void completePageDelivery(Delivery delivery) {
    active(
        () -> {
          state.completePageDelivery(delivery);
          return null;
        });
  }

  /**
   * Restores a reserved snapshot while retaining newer available values.
   *
   * @param delivery previously reserved delivery in this state
   * @throws IllegalStateException if token is consumed/unknown, session invalidated, or state
   *     detached
   * @throws IllegalArgumentException if error-bag merging encounters invalid stored data
   */
  public void abortPageDelivery(Delivery delivery) {
    active(
        () -> {
          state.abortPageDelivery(delivery);
          return null;
        });
  }

  /**
   * Merges pending redirect effects in the existing namespace without rebinding detached state.
   *
   * @param pending values to copy and merge using the single-node store's merge policy
   * @throws IllegalStateException if the session is invalidated or the state is detached
   * @throws IllegalArgumentException if error-bag data cannot be merged
   */
  public void merge(ObjectNode pending) {
    active(
        () -> {
          state.merge(pending);
          return null;
        });
  }
}
