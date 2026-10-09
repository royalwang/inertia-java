package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.io.Serializable;
import java.util.*;

/** Single-node atomic state; also used by the HttpSession adapter. Not a distributed store. */
public final class MemorySessionStore implements SessionStore, Serializable {
  /** Creates empty single-node session state with no reserved deliveries. */
  public MemorySessionStore() {}

  private static final long serialVersionUID = 1L;

  /**
   * Available one-time values.
   *
   * @serial mutable JSON object containing effects not currently reserved for a Page
   */
  private ObjectNode values = JsonNodeFactory.instance.objectNode();

  /**
   * Outstanding delivery reservations.
   *
   * @serial map of delivery tokens to their reserved JSON snapshots
   */
  private final Map<UUID, ObjectNode> reserved = new HashMap<>();

  /**
   * Reads available data without consuming it or exposing the stored mutable node.
   *
   * @param key storage key
   * @return defensive copy, or null when absent
   */
  public synchronized JsonNode get(String key) {
    var value = values.get(key);
    return value == null ? null : value.deepCopy();
  }

  /**
   * Copies one value into available state, replacing the previous key.
   *
   * @param key storage key
   * @param value non-null JSON node; use a JSON null node for a stored null
   * @throws NullPointerException if value is null
   */
  public synchronized void put(String key, JsonNode value) {
    values.set(key, value.deepCopy());
  }

  /**
   * Removes and returns an available value; reserved delivery data is unaffected.
   *
   * @param key storage key
   * @return removed node, no longer retained by this store, or null when absent
   */
  public synchronized JsonNode pull(String key) {
    return values.remove(key);
  }

  /**
   * Reserves all available values under a new token and starts an empty available state.
   *
   * @return defensive snapshot and unique delivery token
   */
  public synchronized Delivery beginPageDelivery() {
    UUID token = UUID.randomUUID();
    var snapshot = values;
    values = JsonNodeFactory.instance.objectNode();
    reserved.put(token, snapshot);
    return new Delivery(token, snapshot);
  }

  /**
   * Removes a reserved token, consuming only its snapshot.
   *
   * @param delivery previously reserved delivery
   * @throws IllegalStateException if the token is unknown or already consumed
   */
  public synchronized void completePageDelivery(Delivery delivery) {
    if (reserved.remove(delivery.token()) == null)
      throw new IllegalStateException("Delivery already completed");
  }

  /**
   * Restores reserved values and overlays newer available values, then consumes the token.
   *
   * <p>Object keys merge shallowly, boolean flags OR together, and error bags append messages.
   * Merge construction happens before publication so a merge failure preserves the prior states.
   *
   * @param delivery previously reserved delivery
   * @throws IllegalStateException if the token is unknown or already consumed
   * @throws IllegalArgumentException if stored error-bag data cannot be decoded
   */
  public synchronized void abortPageDelivery(Delivery delivery) {
    var snapshot = reserved.get(delivery.token());
    if (snapshot == null) throw new IllegalStateException("Delivery already completed");
    var restored = merged(snapshot, values);
    values = restored;
    reserved.remove(delivery.token());
  }

  /**
   * Atomically overlays pending values into available state without consuming reserved tokens.
   *
   * @param pending values to copy and merge using session-effect merge semantics
   * @throws IllegalArgumentException if error-bag data cannot be decoded
   */
  public synchronized void merge(ObjectNode pending) {
    values = merged(values, pending);
  }

  private static ObjectNode merged(ObjectNode original, ObjectNode pending) {
    var values = original.deepCopy();
    pending
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode previous = values.get(entry.getKey());
              JsonNode next = entry.getValue();
              if (entry.getKey().equals(InertiaContext.ERRORS)) {
                values.set(
                    entry.getKey(),
                    ErrorBags.fromJson(previous).merge(ErrorBags.fromJson(next)).toJson());
              } else if (previous != null && previous.isObject() && next.isObject()) {
                var merged = ((ObjectNode) previous).deepCopy();
                merged.setAll((ObjectNode) next);
                values.set(entry.getKey(), merged);
              } else if (previous != null && previous.isBoolean() && next.isBoolean())
                values.put(entry.getKey(), previous.asBoolean() || next.asBoolean());
              else values.set(entry.getKey(), next.deepCopy());
            });
    return values;
  }
}
