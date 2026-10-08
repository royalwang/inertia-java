package io.inertia.spring;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class HttpSessionStoreTest {
  final PageCodec codec = new PageCodec();

  @Test
  void namespacesAreIsolatedAndDefaultStateRemainsCompatible() {
    var session = new MockHttpSession();
    var original = new HttpSessionStore(session);
    original.put(InertiaContext.FLASH, codec.value(Map.of("toast", "default")));
    var alpha = new HttpSessionStore(session, "alpha");
    var beta = new HttpSessionStore(session, "beta");
    alpha.put(InertiaContext.FLASH, codec.value(Map.of("toast", "alpha")));
    beta.put(InertiaContext.FLASH, codec.value(Map.of("toast", "beta")));
    assertEquals(
        "alpha",
        new HttpSessionStore(session, "alpha").get(InertiaContext.FLASH).path("toast").asText());
    assertEquals("beta", beta.get(InertiaContext.FLASH).path("toast").asText());
    assertEquals(
        "default", new HttpSessionStore(session).get(InertiaContext.FLASH).path("toast").asText());
    var delivery = alpha.beginPageDelivery();
    assertNull(alpha.get(InertiaContext.FLASH));
    assertNotNull(beta.get(InertiaContext.FLASH));
    alpha.abortPageDelivery(delivery);
    assertEquals("alpha", alpha.get(InertiaContext.FLASH).path("toast").asText());
  }

  @Test
  void invalidationRejectsEveryOperationAndNeverResurrectsState() {
    var session = new MockHttpSession();
    var store = new HttpSessionStore(session);
    store.put(InertiaContext.FLASH, codec.value(Map.of("toast", "private")));
    var delivery = store.beginPageDelivery();
    session.invalidate();
    assertThrows(IllegalStateException.class, () -> store.get(InertiaContext.FLASH));
    assertThrows(
        IllegalStateException.class, () -> store.put(InertiaContext.FLASH, codec.object()));
    assertThrows(IllegalStateException.class, () -> store.pull(InertiaContext.FLASH));
    assertThrows(IllegalStateException.class, store::beginPageDelivery);
    assertThrows(IllegalStateException.class, () -> store.completePageDelivery(delivery));
    assertThrows(IllegalStateException.class, () -> store.abortPageDelivery(delivery));
    assertThrows(IllegalStateException.class, () -> store.merge(codec.object()));
    assertNull(new HttpSessionStore(new MockHttpSession()).get(InertiaContext.FLASH));
  }

  @Test
  void detachedStateCannotBeUsedAfterNamespaceReplacement() {
    var session = new MockHttpSession();
    var old = new HttpSessionStore(session, "alpha");
    old.put(InertiaContext.FLASH, codec.value(Map.of("toast", "private")));
    session.removeAttribute("io.inertia.session.state.alpha");
    var current = new HttpSessionStore(session, "alpha");
    assertThrows(IllegalStateException.class, () -> old.merge(codec.object()));
    assertThrows(IllegalStateException.class, old::beginPageDelivery);
    assertNull(current.get(InertiaContext.FLASH));
  }

  static class InvalidatingSession extends MockHttpSession {
    boolean enabled;
    int reads;

    @Override
    public Object getAttribute(String name) {
      if (enabled && name.equals("io.inertia.session.state") && ++reads == 2) {
        enabled = false;
        invalidate();
      }
      return super.getAttribute(name);
    }
  }

  @Test
  void postOperationCheckDetectsInvalidationDuringAccess() {
    var session = new InvalidatingSession();
    var store = new HttpSessionStore(session);
    store.put(InertiaContext.FLASH, codec.value(Map.of("toast", "private")));
    session.enabled = true;
    assertThrows(IllegalStateException.class, () -> store.get(InertiaContext.FLASH));
  }

  @Test
  void invalidNamespaceAndInitializationWriteFailureAreReported() {
    var session = new MockHttpSession();
    for (String namespace : List.of("", "../app", "a/b", " ", "a".repeat(65)))
      assertThrows(IllegalArgumentException.class, () -> new HttpSessionStore(session, namespace));
    assertFalse(session.getAttributeNames().hasMoreElements());
    var failure =
        new MockHttpSession() {
          @Override
          public void setAttribute(String name, Object value) {
            throw new IllegalStateException("storage write failed");
          }
        };
    assertThrows(IllegalStateException.class, () -> new HttpSessionStore(failure));
  }
}
