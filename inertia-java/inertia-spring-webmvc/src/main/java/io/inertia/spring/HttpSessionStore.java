package io.inertia.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.inertia.core.*;
import jakarta.servlet.http.HttpSession;

/**
 * In-process HttpSession reservation. Distributed Spring Session requires another implementation.
 */
public final class HttpSessionStore implements SessionStore {
  private static final String KEY = "io.inertia.session.state";
  private final MemorySessionStore state;

  public HttpSessionStore(HttpSession session) {
    synchronized (org.springframework.web.util.WebUtils.getSessionMutex(session)) {
      Object value = session.getAttribute(KEY);
      if (value == null) {
        value = new MemorySessionStore();
        session.setAttribute(KEY, value);
      }
      state = (MemorySessionStore) value;
    }
  }

  public JsonNode get(String key) {
    return state.get(key);
  }

  public void put(String key, JsonNode value) {
    state.put(key, value);
  }

  public JsonNode pull(String key) {
    return state.pull(key);
  }

  public Delivery beginPageDelivery() {
    return state.beginPageDelivery();
  }

  public void completePageDelivery(Delivery delivery) {
    state.completePageDelivery(delivery);
  }

  public void abortPageDelivery(Delivery delivery) {
    state.abortPageDelivery(delivery);
  }

  public void merge(ObjectNode pending) {
    state.merge(pending);
  }
}
