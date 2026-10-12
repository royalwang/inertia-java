package io.inertia.example;

import io.inertia.core.*;
import io.inertia.redis.RedisSessionException;
import io.inertia.spring.InertiaSessionStoreFactory;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Copied only into isolated local consumers, never shipped in the example artifact. */
@RestController
@ConditionalOnProperty(name = "qualification.auth", havingValue = "true")
public class AuthProbe {
  private final InertiaSessionStoreFactory stores;
  private final PageCodec codec;
  private final Map<UUID, Held> held = new ConcurrentHashMap<>();
  private record Held(SessionStore store, SessionStore.Delivery delivery) {}

  public AuthProbe(InertiaSessionStoreFactory stores, PageCodec codec) {
    this.stores = stores;
    this.codec = codec;
  }

  @PostMapping("/probe/seed")
  public Map<String, Boolean> seed(HttpServletRequest request) {
    request.getSession().setAttribute("qualification.marker", "preserved");
    stores.create(request).put(InertiaContext.FLASH, codec.value(Map.of("toast", "old-identity")));
    return Map.of("seeded", true);
  }

  @PostMapping("/probe/hold")
  public Map<String, String> hold(HttpServletRequest request) {
    var store = stores.create(request);
    var handle = UUID.randomUUID();
    held.put(handle, new Held(store, store.beginPageDelivery()));
    return Map.of("handle", handle.toString());
  }

  @PostMapping("/probe/late")
  public Map<String, Boolean> late(@RequestParam UUID handle) {
    var entry = held.remove(handle);
    if (entry == null) throw new IllegalArgumentException("Unknown owned probe");
    try {
      entry.store().abortPageDelivery(entry.delivery());
      entry.store().put(InertiaContext.FLASH, codec.value(Map.of("toast", "resurrected")));
      return Map.of("rejected", false);
    } catch (RedisSessionException stale) {
      if (stale.reason() != RedisSessionException.Reason.STALE_DOMAIN) throw stale;
      return Map.of("rejected", true);
    }
  }

  @PostMapping("/probe/expire")
  public Map<String, String> expire(HttpServletRequest request) {
    request.getSession().setMaxInactiveInterval(2);
    return hold(request);
  }

  @GetMapping("/probe/state")
  public Map<String, Object> state(HttpServletRequest request) {
    var session = request.getSession();
    return Map.of("marker", "preserved".equals(session.getAttribute("qualification.marker")),
        "authenticated", session.getAttribute("SPRING_SECURITY_CONTEXT") != null);
  }
}
