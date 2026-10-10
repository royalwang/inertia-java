package io.inertia.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.inertia.core.SessionStore;
import io.inertia.redis.*;
import io.inertia.spring.InertiaSessionStoreFactory;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.web.util.WebUtils;

/**
 * Request store factory tied to trusted HttpSession ID and persisted Redis epoch metadata.
 * Implements Servlet destruction/ID-change hooks. Register this exact instance for both listener
 * interfaces when using plain Servlet integration; Boot registers it automatically. Host session
 * sharing is separate from Redis delivery and belongs to the application. A host adapter must
 * propagate its lifecycle events; this class cannot infer remote logout from a cached session.
 * Listener storage failures propagate instead of pretending revocation succeeded.
 */
public final class RedisHttpSessionStoreFactory
    implements InertiaSessionStoreFactory, HttpSessionListener, HttpSessionIdListener {
  private final RedisSessionBackend backend;
  private final RedisSessionOptions options;
  private final String attribute;

  /**
   * Creates an application-scoped factory without taking ownership of backend shutdown.
   *
   * @param backend owned standalone Redis transport
   * @param options delivery namespace and bounds
   */
  public RedisHttpSessionStoreFactory(RedisSessionBackend backend, RedisSessionOptions options) {
    this.backend = Objects.requireNonNull(backend);
    this.options = Objects.requireNonNull(options);
    attribute = "io.inertia.redis.epoch." + options.namespace();
  }

  /**
   * Pins this request to its host-session identity and persisted expected epoch.
   *
   * @param request current Servlet request; session identity is never read from arbitrary headers
   * @return request-owned handle checking host attachment before and after each operation
   */
  @Override
  public SessionStore create(HttpServletRequest request) {
    var session = request.getSession();
    Object mutex = WebUtils.getSessionMutex(session);
    synchronized (mutex) {
      String identity = session.getId();
      Metadata metadata = metadata(session.getAttribute(attribute));
      if (metadata != null && !metadata.identity().equals(identity)) {
        revokeDomain(
            metadata); // a host migration may have copied attributes from a revoked identity
        session.removeAttribute(attribute);
        metadata = null;
      }
      if (metadata != null && metadata.revoking())
        throw new RedisSessionException(RedisSessionException.Reason.STALE_DOMAIN);
      var store =
          new RedisSessionStore(
              backend, options, identity, metadata == null ? null : metadata.epoch());
      String attached = new Metadata(identity, store.epoch(), false).encode();
      if (metadata == null) session.setAttribute(attribute, attached);
      return new Attached(session, mutex, identity, attached, store);
    }
  }

  /**
   * Revokes old identity and clears host epoch metadata before a rotated ID is used.
   *
   * @param event Servlet host-session event
   * @param oldSessionId trusted previous identity provided by the host container
   */
  @Override
  public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {
    revoke(event.getSession(), oldSessionId);
  }

  /**
   * Revokes destroyed host identity and removes attachment metadata.
   *
   * @param event Servlet host-session event containing the old session and attributes
   */
  @Override
  public void sessionDestroyed(HttpSessionEvent event) {
    revoke(event.getSession(), event.getSession().getId());
  }

  private void revoke(HttpSession session, String identity) {
    synchronized (WebUtils.getSessionMutex(session)) {
      Metadata metadata = metadata(session.getAttribute(attribute));
      if (metadata == null || !metadata.identity().equals(identity)) return;
      String pending = new Metadata(identity, metadata.epoch(), true).encode();
      session.setAttribute(attribute, pending);
      // Retain epoch/identity on failure so a later explicit host operation can reconcile
      // revocation.
      revokeDomain(metadata);
      if (pending.equals(session.getAttribute(attribute))) session.removeAttribute(attribute);
    }
  }

  /**
   * Revokes the trusted current host identity before a host invalidation or ID change. Failure
   * prevents the host operation; pending metadata keeps in-flight handles detached.
   *
   * @param session trusted current host session, never a user-selected identity
   */
  public void beforeHostChange(HttpSession session) {
    revoke(session, session.getId());
  }

  private void revokeDomain(Metadata metadata) {
    try {
      new RedisSessionStore(backend, options, metadata.identity(), metadata.epoch()).invalidate();
    } catch (RedisSessionException failure) {
      if (failure.reason() != RedisSessionException.Reason.STALE_DOMAIN) throw failure;
      // A known read of a revoked/missing epoch is reconciliation, not replay of an unknown
      // mutation.
    }
  }

  private record Metadata(String identity, UUID epoch, boolean revoking) {
    String encode() {
      return (revoking ? "revoking" : "active")
          + "."
          + epoch
          + "."
          + Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  private static Metadata metadata(Object value) {
    if (value == null) return null;
    if (!(value instanceof String text) || text.length() > 1500)
      throw new IllegalStateException("Invalid Redis host metadata");
    try {
      String[] fields = text.split("\\.", -1);
      if (fields.length != 3 || !Set.of("active", "revoking").contains(fields[0]))
        throw new IllegalArgumentException();
      return new Metadata(
          new String(
              Base64.getUrlDecoder().decode(fields[2]), java.nio.charset.StandardCharsets.UTF_8),
          UUID.fromString(fields[1]),
          fields[0].equals("revoking"));
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid Redis host metadata");
    }
  }

  private final class Attached implements SessionStore {
    private final HttpSession session;
    private final Object mutex;
    private final String identity;
    private final RedisSessionStore store;
    private final String metadata;

    Attached(
        HttpSession session,
        Object mutex,
        String identity,
        String metadata,
        RedisSessionStore store) {
      this.session = session;
      this.mutex = mutex;
      this.identity = identity;
      this.metadata = metadata;
      this.store = store;
    }

    private void attached() {
      if (!identity.equals(session.getId()) || !metadata.equals(session.getAttribute(attribute)))
        throw new RedisSessionException(RedisSessionException.Reason.STALE_DOMAIN);
    }

    private <T> T active(Supplier<T> action) {
      synchronized (mutex) {
        attached();
        T result = action.get();
        attached();
        return result;
      }
    }

    @Override
    public JsonNode get(String key) {
      return active(() -> store.get(key));
    }

    @Override
    public void put(String key, JsonNode value) {
      active(
          () -> {
            store.put(key, value);
            return null;
          });
    }

    @Override
    public JsonNode pull(String key) {
      return active(() -> store.pull(key));
    }

    @Override
    public Delivery beginPageDelivery() {
      return active(store::beginPageDelivery);
    }

    @Override
    public void completePageDelivery(Delivery value) {
      active(
          () -> {
            store.completePageDelivery(value);
            return null;
          });
    }

    @Override
    public void abortPageDelivery(Delivery value) {
      active(
          () -> {
            store.abortPageDelivery(value);
            return null;
          });
    }

    @Override
    public void merge(ObjectNode pending) {
      active(
          () -> {
            store.merge(pending);
            return null;
          });
    }
  }
}
