package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Implementations must reserve and restore snapshots atomically in their storage domain. */
public interface SessionStore {
  record Delivery(UUID token, ObjectNode data) {}

  JsonNode get(String key);

  void put(String key, JsonNode value);

  JsonNode pull(String key);

  Delivery beginPageDelivery();

  void completePageDelivery(Delivery delivery);

  void abortPageDelivery(Delivery delivery);

  void merge(ObjectNode pending);
}
