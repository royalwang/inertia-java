package io.inertia.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.inertia.core.*;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Single-node namespace state. Invalidated or detached state is never copied into a new session.
 */
public final class HttpSessionStore implements SessionStore {
  private static final String KEY = "io.inertia.session.state";
  private final HttpSession session;
  private final Object mutex;
  private final String attribute;
  private final MemorySessionStore state;

  public HttpSessionStore(HttpSession session) {
    this(session, DEFAULT_NAMESPACE);
  }

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

  public JsonNode get(String key) {
    return active(() -> state.get(key));
  }

  public void put(String key, JsonNode value) {
    active(
        () -> {
          state.put(key, value);
          return null;
        });
  }

  public JsonNode pull(String key) {
    return active(() -> state.pull(key));
  }

  public Delivery beginPageDelivery() {
    return active(state::beginPageDelivery);
  }

  public void completePageDelivery(Delivery delivery) {
    active(
        () -> {
          state.completePageDelivery(delivery);
          return null;
        });
  }

  public void abortPageDelivery(Delivery delivery) {
    active(
        () -> {
          state.abortPageDelivery(delivery);
          return null;
        });
  }

  public void merge(ObjectNode pending) {
    active(
        () -> {
          state.merge(pending);
          return null;
        });
  }
}
