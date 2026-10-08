package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.inertia.core.*;
import io.inertia.spring.HttpSessionStore;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

/** Synthetic servlet principals verify adapter isolation; this is not a login implementation. */
@SpringBootTest(classes = MvcIsolationTest.TestApplication.class)
@AutoConfigureMockMvc
class MvcIsolationTest {
  @Autowired MockMvc mvc;
  static final CountDownLatch entered = new CountDownLatch(2);

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
  @Import(Pages.class)
  static class TestApplication {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Profile"));
    }
  }

  @Controller
  static class Pages {
    @GetMapping("/profile")
    InertiaResponse profile(InertiaContext context, Principal principal) {
      String publicName = principal.getName(); // Capture the DTO on the request thread.
      context.share("publicUser", Map.of("name", publicName));
      return context.render(
          "Profile",
          Props.builder()
              .put(
                  "identity",
                  Prop.lazy(
                      () -> {
                        entered.countDown();
                        if (!entered.await(2, TimeUnit.SECONDS))
                          throw new IllegalStateException("Concurrent request did not enter");
                        return Map.of("name", publicName);
                      }))
              .build());
    }
  }

  @Test
  void concurrentPrincipalsPropsAndSessionEffectsNeverCrossRequests() throws Exception {
    var codec = new PageCodec();
    var alice = new MockHttpSession();
    var bob = new MockHttpSession();
    new HttpSessionStore(alice)
        .put(InertiaContext.FLASH, codec.value(Map.of("toast", "Alice-only")));
    new HttpSessionStore(bob).put(InertiaContext.FLASH, codec.value(Map.of("toast", "Bob-only")));
    try (var requests = Executors.newFixedThreadPool(2)) {
      var first =
          requests.submit(
              () ->
                  mvc.perform(
                          get("/profile")
                              .session(alice)
                              .principal(() -> "Alice")
                              .header("X-Inertia", "true")
                              .header("X-Inertia-Version", "v1"))
                      .andReturn()
                      .getResponse());
      var second =
          requests.submit(
              () ->
                  mvc.perform(
                          get("/profile")
                              .session(bob)
                              .principal(() -> "Bob")
                              .header("X-Inertia", "true")
                              .header("X-Inertia-Version", "v1"))
                      .andReturn()
                      .getResponse());
      var a = first.get(5, TimeUnit.SECONDS);
      var b = second.get(5, TimeUnit.SECONDS);
      assertEquals(200, a.getStatus());
      assertEquals(200, b.getStatus());
      var pageA = codec.read(a.getContentAsString());
      var pageB = codec.read(b.getContentAsString());
      assertEquals("Alice", pageA.at("/props/publicUser/name").asText());
      assertEquals("Alice", pageA.at("/props/identity/name").asText());
      assertEquals("Alice-only", pageA.at("/flash/toast").asText());
      assertEquals("Bob", pageB.at("/props/publicUser/name").asText());
      assertEquals("Bob", pageB.at("/props/identity/name").asText());
      assertEquals("Bob-only", pageB.at("/flash/toast").asText());
      assertFalse(a.getContentAsString().contains("Bob"));
      assertFalse(b.getContentAsString().contains("Alice"));
      assertNull(new HttpSessionStore(alice).get(InertiaContext.FLASH));
      assertNull(new HttpSessionStore(bob).get(InertiaContext.FLASH));
    }
  }
}
