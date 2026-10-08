package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.io.Serializable;
import java.util.*;

/** Single-node atomic state; also used by the HttpSession adapter. Not a distributed store. */
public final class MemorySessionStore implements SessionStore, Serializable {
  private static final long serialVersionUID = 1L;
  private ObjectNode values = JsonNodeFactory.instance.objectNode();
  private final Map<UUID, ObjectNode> reserved = new HashMap<>();

  public synchronized JsonNode get(String key) {
    var value = values.get(key);
    return value == null ? null : value.deepCopy();
  }

  public synchronized void put(String key, JsonNode value) {
    values.set(key, value.deepCopy());
  }

  public synchronized JsonNode pull(String key) {
    return values.remove(key);
  }

  public synchronized Delivery beginPageDelivery() {
    UUID token = UUID.randomUUID();
    var snapshot = values;
    values = JsonNodeFactory.instance.objectNode();
    reserved.put(token, snapshot);
    return new Delivery(token, snapshot.deepCopy());
  }

  public synchronized void completePageDelivery(Delivery delivery) {
    if (reserved.remove(delivery.token()) == null)
      throw new IllegalStateException("Delivery already completed");
  }

  public synchronized void abortPageDelivery(Delivery delivery) {
    var snapshot = reserved.remove(delivery.token());
    if (snapshot == null) throw new IllegalStateException("Delivery already completed");
    ObjectNode newer = values;
    values = snapshot;
    merge(newer);
  }

  public synchronized void merge(ObjectNode pending) {
    pending
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode previous = values.get(entry.getKey());
              JsonNode next = entry.getValue();
              if (previous != null && previous.isObject() && next.isObject()) {
                var merged = ((ObjectNode) previous).deepCopy();
                merged.setAll((ObjectNode) next);
                values.set(entry.getKey(), merged);
              } else if (previous != null && previous.isBoolean() && next.isBoolean())
                values.put(entry.getKey(), previous.asBoolean() || next.asBoolean());
              else values.set(entry.getKey(), next.deepCopy());
            });
  }
}
