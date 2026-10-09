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
  /**
   * Default adapter namespace; distinct applications sharing a session should choose distinct
   * names.
   */
  String DEFAULT_NAMESPACE = "default";

  /**
   * Validates an adapter's session namespace before constructing session keys.
   *
   * @param namespace identifier of 1-64 ASCII letters, digits, dots, underscores, or hyphens,
   *     starting with a letter or digit
   * @return the unchanged valid namespace
   * @throws IllegalArgumentException if the namespace is null or invalid
   */
  static String requireNamespace(String namespace) {
    if (namespace == null || !namespace.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
      throw new IllegalArgumentException(
          "Inertia session namespace must be 1-64 safe identifier characters");
    return namespace;
  }

  /**
   * Reserved one-time Page data and its opaque delivery token.
   *
   * @param token non-null store-generated transaction identity
   * @param data non-null reserved values; copied at construction and on access
   */
  record Delivery(UUID token, ObjectNode data) {
    /**
     * Creates a delivery snapshot without exposing the supplied mutable JSON object.
     *
     * @throws NullPointerException if the token or data is null
     */
    public Delivery {
      Objects.requireNonNull(token);
      data = Objects.requireNonNull(data).deepCopy();
    }

    /**
     * Returns reserved data without exposing this delivery's internal snapshot.
     *
     * @return independent mutable copy of the reserved values
     */
    @Override
    public ObjectNode data() {
      return data.deepCopy();
    }
  }

  /**
   * Reads a stored value without consuming it.
   *
   * @param key storage key within this store's namespace
   * @return current value, or null if absent
   */
  JsonNode get(String key);

  /**
   * Stores one value under a key.
   *
   * @param key storage key within this store's namespace
   * @param value JSON value to store
   */
  void put(String key, JsonNode value);

  /**
   * Reads and removes one stored value.
   *
   * @param key storage key within this store's namespace
   * @return removed value, or null if absent
   */
  JsonNode pull(String key);

  /**
   * Atomically reserves currently available one-time data for one Page render.
   *
   * <p>A failure must leave the stored data available. New values written while the delivery is in
   * flight belong to later delivery and must not be consumed by completing this token.
   *
   * @return token and snapshot to pass to exactly one terminal delivery operation
   */
  Delivery beginPageDelivery();

  /**
   * Consumes a reserved delivery token at most once after render preparation succeeds.
   *
   * <p>Completion describes the server render transaction, not browser receipt. A later HTTP write
   * failure cannot undo a completed delivery. Report storage errors so the context can fail closed;
   * do not silently accept an unknown outcome.
   *
   * @param delivery delivery previously reserved by this store
   */
  void completePageDelivery(Delivery delivery);

  /**
   * Restores reserved data while preserving values written after reservation.
   *
   * <p>The token must be consumed at most once. The context may attempt restoration once after a
   * completion failure; implementations must report invalid tokens and storage failures rather than
   * retrying an operation whose storage outcome is unknown.
   *
   * @param delivery delivery previously reserved by this store
   */
  void abortPageDelivery(Delivery delivery);

  /**
   * Atomically merges pending redirect state into the available one-time values.
   *
   * <p>Implementations must retain the existing values unchanged on failure. Merge semantics must
   * preserve independent error bags and newer values consistently with the adapter's session store.
   *
   * @param pending pending state to make available to a subsequent Page
   */
  void merge(ObjectNode pending);
}
