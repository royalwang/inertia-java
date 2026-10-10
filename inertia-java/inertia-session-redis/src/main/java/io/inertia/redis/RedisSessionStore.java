package io.inertia.redis;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.inertia.core.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Function;

/**
 * Atomic one-time JSON delivery for a trusted host session in one standalone Redis domain. Each
 * handle pins an epoch; expiry, invalidation and storage loss never silently recreate it. Uses
 * opaque-byte CAS and Redis time. Explicit CAS conflicts may be recomputed, but network failures
 * have unknown outcomes and are never automatically replayed. Lease recovery invalidates the old
 * token atomically with restoring data. Does not guarantee browser receipt or survival across Redis
 * data loss/failover. Identity must come from authenticated host session state.
 */
public final class RedisSessionStore implements SessionStore {
  private final RedisSessionBackend backend;
  private final RedisSessionOptions options;
  private final String key;
  private final UUID epoch;
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  /**
   * Attaches a trusted identity to an existing domain, or creates it on first host-session use.
   *
   * @param backend application-owned transport; this handle never closes it
   * @param options namespace and capacity/time bounds
   * @param identity nonempty trusted host identity, at most 1024 characters; never a request
   *     parameter
   * @param expectedEpoch epoch persisted in host session state, or null only on first attachment
   * @throws RedisSessionException if the domain is stale, storage fails or bounds are exceeded
   * @throws IllegalArgumentException if identity is invalid
   */
  public RedisSessionStore(
      RedisSessionBackend backend,
      RedisSessionOptions options,
      String identity,
      UUID expectedEpoch) {
    this.backend = Objects.requireNonNull(backend);
    this.options = Objects.requireNonNull(options);
    if (identity == null || identity.isBlank() || identity.length() > 1024)
      throw new IllegalArgumentException("Invalid trusted host identity");
    try {
      key =
          "io.inertia.delivery.{"
              + options.namespace()
              + ":"
              + HexFormat.of()
                  .formatHex(
                      MessageDigest.getInstance("SHA-256")
                          .digest(identity.getBytes(StandardCharsets.UTF_8)))
              + "}";
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    epoch = attach(expectedEpoch);
  }

  /**
   * Returns the pinned epoch for storage in trusted host-session metadata.
   *
   * @return immutable domain generation
   */
  public UUID epoch() {
    return epoch;
  }

  private UUID attach(UUID expected) {
    long budget = System.nanoTime() + java.time.Duration.ofSeconds(1).toNanos();
    for (int attempt = 0; attempt < options.maxAttempts(); attempt++) {
      requireBudget(budget);
      var read = backend.read(key);
      if (!read.json().isEmpty()) {
        var state = decode(read);
        if (state.path("revoked").asBoolean()
            || expected != null && !expected.toString().equals(state.path("epoch").asText()))
          throw fail(RedisSessionException.Reason.STALE_DOMAIN);
        return UUID.fromString(state.path("epoch").asText());
      }
      if (expected != null) throw fail(RedisSessionException.Reason.STALE_DOMAIN);
      var state = mapper.createObjectNode();
      UUID created = UUID.randomUUID();
      state
          .put("schemaVersion", 1)
          .put("epoch", created.toString())
          .put("revision", 0L)
          .put("lastTime", read.now())
          .put("revoked", false);
      state.putObject("available");
      state.putObject("reserved");
      state.putObject("terminals");
      requireBudget(budget);
      long result =
          backend.cas(
              key,
              read,
              encode(state),
              writeDeadline(budget, read.now()),
              options.idleTtl().toMillis());
      if (result == 1) return created;
      if (result == -2) throw fail(RedisSessionException.Reason.CLOCK_REVERSED);
    }
    throw fail(RedisSessionException.Reason.CONTENTION);
  }

  private ObjectNode decode(RedisSessionBackend.Read read) {
    if (read.json().getBytes(StandardCharsets.UTF_8).length > options.maxBytes())
      throw fail(RedisSessionException.Reason.CAPACITY);
    try {
      var node = mapper.readTree(read.json());
      if (!(node instanceof ObjectNode state)
          || !state.path("schemaVersion").isInt()
          || state.path("schemaVersion").intValue() != 1
          || !state.path("revision").isIntegralNumber()
          || !state.path("revision").canConvertToLong()
          || state.path("revision").longValue() < 0
          || state.path("revision").longValue() == Long.MAX_VALUE
          || !state.path("lastTime").isIntegralNumber()
          || !state.path("lastTime").canConvertToLong()
          || !state.path("revoked").isBoolean()
          || !state.path("available").isObject()
          || !state.path("reserved").isObject()
          || !state.path("terminals").isObject())
        throw fail(RedisSessionException.Reason.INVALID_STATE);
      UUID.fromString(state.path("epoch").asText());
      if (state.path("lastTime").longValue() > read.now())
        throw fail(RedisSessionException.Reason.CLOCK_REVERSED);
      if (state.path("reserved").size() > options.maxReservations()
          || state.path("terminals").size() > options.maxTerminals())
        throw fail(RedisSessionException.Reason.CAPACITY);
      for (var entries = state.path("reserved").fields(); entries.hasNext(); ) {
        var entry = entries.next();
        UUID.fromString(entry.getKey());
        var value = entry.getValue();
        if (!value.isObject()
            || !value.path("data").isObject()
            || !value.path("expiresAt").isIntegralNumber()
            || !value.path("expiresAt").canConvertToLong()
            || value.path("expiresAt").longValue() < 0
            || !value.path("sequence").isIntegralNumber()
            || !value.path("sequence").canConvertToLong()
            || value.path("sequence").longValue() < 0
            || value.path("sequence").longValue() > state.path("revision").longValue())
          throw fail(RedisSessionException.Reason.INVALID_STATE);
      }
      for (var entries = state.path("terminals").fields(); entries.hasNext(); ) {
        var entry = entries.next();
        UUID.fromString(entry.getKey());
        if (!entry.getValue().isIntegralNumber()
            || !entry.getValue().canConvertToLong()
            || entry.getValue().longValue() < 0)
          throw fail(RedisSessionException.Reason.INVALID_STATE);
      }
      return state;
    } catch (RedisSessionException classified) {
      throw classified;
    } catch (Exception invalid) {
      throw fail(RedisSessionException.Reason.INVALID_STATE);
    }
  }

  private String encode(ObjectNode state) {
    try {
      String json = mapper.writeValueAsString(state);
      if (json.getBytes(StandardCharsets.UTF_8).length > options.maxBytes())
        throw fail(RedisSessionException.Reason.CAPACITY);
      return json;
    } catch (RedisSessionException classified) {
      throw classified;
    } catch (Exception invalid) {
      throw fail(RedisSessionException.Reason.INVALID_STATE);
    }
  }

  private static ObjectNode object(ObjectNode state, String field) {
    return (ObjectNode) state.get(field);
  }

  // Reuse the canonical merge including error bags; no Redis Lua JSON re-encoding.
  private static ObjectNode merged(ObjectNode older, ObjectNode newer) {
    var memory = new MemorySessionStore();
    older.fields().forEachRemaining(e -> memory.put(e.getKey(), e.getValue()));
    memory.merge(newer);
    return memory.beginPageDelivery().data();
  }

  private void terminal(ObjectNode state, String token, long now) {
    var terminals = object(state, "terminals");
    if (terminals.size() >= options.maxTerminals())
      throw fail(RedisSessionException.Reason.CAPACITY);
    terminals.put(token, now + options.terminalRetention().toMillis());
  }

  private void recover(ObjectNode state, long now) {
    var terminals = object(state, "terminals");
    var expired = new ArrayList<String>();
    terminals
        .fields()
        .forEachRemaining(
            e -> {
              if (e.getValue().longValue() <= now) expired.add(e.getKey());
            });
    terminals.remove(expired);
    var reserved = object(state, "reserved");
    var leases = new ArrayList<Map.Entry<String, JsonNode>>();
    reserved
        .fields()
        .forEachRemaining(
            e -> {
              if (e.getValue().path("expiresAt").longValue() <= now) leases.add(e);
            });
    // Restore newest first so available (and later reservations) retain priority over older state.
    leases.sort(
        Comparator.<Map.Entry<String, JsonNode>>comparingLong(
                e -> e.getValue().path("sequence").longValue())
            .reversed());
    for (var entry : leases) {
      state.set(
          "available",
          merged((ObjectNode) entry.getValue().get("data"), object(state, "available")));
      terminal(state, entry.getKey(), now);
      reserved.remove(entry.getKey());
    }
  }

  private <T> T change(Function<ObjectNode, T> mutation) {
    long budget = System.nanoTime() + java.time.Duration.ofSeconds(1).toNanos();
    for (int attempt = 0; attempt < options.maxAttempts(); attempt++) {
      requireBudget(budget);
      var read = backend.read(key);
      if (read.json().isEmpty()) throw fail(RedisSessionException.Reason.STALE_DOMAIN);
      var state = decode(read);
      if (!epoch.toString().equals(state.path("epoch").asText())
          || state.path("revoked").asBoolean())
        throw fail(RedisSessionException.Reason.STALE_DOMAIN);
      long deadline = read.now() + 1000;
      for (var entries = state.path("reserved").elements(); entries.hasNext(); ) {
        long expiry = entries.next().path("expiresAt").longValue();
        if (expiry > read.now()) deadline = Math.min(deadline, expiry);
      }
      recover(state, read.now());
      state.put("lastTime", read.now());
      state.put("revision", state.path("revision").longValue() + 1);
      T value = mutation.apply(state);
      for (var entries = state.path("reserved").elements(); entries.hasNext(); )
        deadline = Math.min(deadline, entries.next().path("expiresAt").longValue());
      deadline = Math.min(deadline, writeDeadline(budget, read.now()));
      long result = backend.cas(key, read, encode(state), deadline, options.idleTtl().toMillis());
      if (result == 1) return value;
      if (result == -2) throw fail(RedisSessionException.Reason.CLOCK_REVERSED);
      // Only explicit no-write conflicts or stale read deadlines permit recomputation.
    }
    throw fail(RedisSessionException.Reason.CONTENTION);
  }

  /**
   * Reads a defensive copy of available data and performs bounded lease cleanup.
   *
   * @param key available value name
   * @return value, or null when absent
   */
  @Override
  public JsonNode get(String key) {
    Objects.requireNonNull(key);
    return change(
        state -> {
          var value = object(state, "available").get(key);
          return value == null ? null : value.deepCopy();
        });
  }

  /**
   * Replaces an available JSON value without consuming reservations.
   *
   * @param key non-null value name
   * @param value non-null JSON value, copied before publication
   */
  @Override
  public void put(String key, JsonNode value) {
    Objects.requireNonNull(key);
    validateJson(Objects.requireNonNull(value));
    var copy = value.deepCopy();
    change(
        state -> {
          object(state, "available").set(key, copy);
          return null;
        });
  }

  /**
   * Removes one available value atomically.
   *
   * @param key non-null value name
   * @return removed defensive value, or null
   */
  @Override
  public JsonNode pull(String key) {
    Objects.requireNonNull(key);
    return change(state -> object(state, "available").remove(key));
  }

  /**
   * Reserves available values under a bounded lease and an opaque token.
   *
   * @return defensive snapshot with a new transaction identity
   */
  @Override
  public Delivery beginPageDelivery() {
    return change(
        state -> {
          var reserved = object(state, "reserved");
          if (reserved.size() >= options.maxReservations())
            throw fail(RedisSessionException.Reason.CAPACITY);
          UUID token = UUID.randomUUID();
          var data = object(state, "available");
          reserved
              .putObject(token.toString())
              .put("expiresAt", state.path("lastTime").longValue() + options.lease().toMillis())
              .put("sequence", state.path("revision").longValue())
              .set("data", data);
          state.set("available", mapper.createObjectNode());
          return new Delivery(token, data);
        });
  }

  /**
   * Consumes only the matching live reservation, once.
   *
   * @param delivery previously reserved delivery; caller-supplied snapshot is never trusted
   */
  @Override
  public void completePageDelivery(Delivery delivery) {
    finish(delivery, false);
  }

  /**
   * Restores the matching live reservation, preserving newer available values.
   *
   * @param delivery previously reserved delivery; caller-supplied snapshot is never trusted
   */
  @Override
  public void abortPageDelivery(Delivery delivery) {
    finish(delivery, true);
  }

  private void finish(Delivery delivery, boolean abort) {
    Objects.requireNonNull(delivery);
    change(
        state -> {
          String token = delivery.token().toString();
          var reserved = object(state, "reserved");
          var value = reserved.get(token);
          if (value == null) throw fail(RedisSessionException.Reason.INVALID_TOKEN);
          if (abort)
            state.set(
                "available", merged((ObjectNode) value.get("data"), object(state, "available")));
          terminal(state, token, state.path("lastTime").longValue());
          reserved.remove(token);
          return null;
        });
  }

  /**
   * Merges redirect state using canonical core merge semantics before atomic publication.
   *
   * @param pending non-null effects copied at entry
   */
  @Override
  public void merge(ObjectNode pending) {
    validateJson(Objects.requireNonNull(pending));
    var copy = pending.deepCopy();
    change(
        state -> {
          state.set("available", merged(object(state, "available"), copy));
          return null;
        });
  }

  /**
   * Atomically revokes this identity domain and outstanding tokens; never deletes the key. New
   * identity rotation must use a new trusted host identity. Tombstone expires after idleTtl;
   * persisted expectedEpoch prevents old host metadata recreating missing state afterwards.
   */
  public void invalidate() {
    change(
        state -> {
          state.put("revoked", true);
          state.set("available", mapper.createObjectNode());
          state.set("reserved", mapper.createObjectNode());
          state.set("terminals", mapper.createObjectNode());
          return null;
        });
  }

  /**
   * Recovers expired leases atomically with invalidating their old tokens. Applications may call
   * this on known active domains; ordinary operations also perform cleanup.
   */
  public void recoverExpired() {
    change(state -> null);
  }

  private static void validateJson(JsonNode value) {
    record Node(JsonNode value, int depth) {}
    var pending = new ArrayDeque<Node>();
    pending.push(new Node(value, 0));
    int count = 0;
    while (!pending.isEmpty()) {
      var entry = pending.pop();
      var node = entry.value();
      if (++count > 65536 || entry.depth() > 48 || node.isNumber() && node.asText().length() > 1000)
        throw fail(RedisSessionException.Reason.CAPACITY);
      if (node.isPojo()
          || node.isBinary()
          || node.isMissingNode()
          || (node instanceof DoubleNode || node instanceof FloatNode)
              && !Double.isFinite(node.doubleValue()))
        throw new IllegalArgumentException("Redis session values must use finite JSON literals");
      node.elements().forEachRemaining(child -> pending.push(new Node(child, entry.depth() + 1)));
    }
  }

  private static long writeDeadline(long budget, long redisTime) {
    requireBudget(budget);
    return redisTime
        + Math.max(
            1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(budget - System.nanoTime()));
  }

  private static void requireBudget(long deadline) {
    if (System.nanoTime() >= deadline) throw fail(RedisSessionException.Reason.BUDGET_EXHAUSTED);
  }

  private static RedisSessionException fail(RedisSessionException.Reason reason) {
    return new RedisSessionException(reason);
  }
}
