package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/**
 * Single delivery transactions in a storage domain. begin/merge must be atomic on failure;
 * complete/abort must consume a token at most once. Storage failures must be reported, not ignored.
 * Context does not retry unknown outcomes; restoration is attempted once after completion failure.
 */
public interface SessionStore {
  String DEFAULT_NAMESPACE = "default";

  static String requireNamespace(String namespace) {
    if (namespace == null || !namespace.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
      throw new IllegalArgumentException(
          "Inertia session namespace must be 1-64 safe identifier characters");
    return namespace;
  }

  record Delivery(UUID token, ObjectNode data) {
    public Delivery {
      Objects.requireNonNull(token);
      data = Objects.requireNonNull(data).deepCopy();
    }

    @Override
    public ObjectNode data() {
      return data.deepCopy();
    }
  }

  JsonNode get(String key);

  void put(String key, JsonNode value);

  JsonNode pull(String key);

  Delivery beginPageDelivery();

  void completePageDelivery(Delivery delivery);

  void abortPageDelivery(Delivery delivery);

  void merge(ObjectNode pending);
}
