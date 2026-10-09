package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.InertiaErrorPage;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(classes = MvcRequiredSsrTest.App.class, properties = "inertia.all-errors=true")
@AutoConfigureMockMvc
class MvcRequiredSsrTest {
  @Autowired MockMvc mvc;
  static final AtomicInteger calls = new AtomicInteger();
  static final AtomicInteger errors = new AtomicInteger();

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({Pages.class, Api.class, SecurityConfiguration.class})
  static class App {
    @Bean
    InertiaConfig config() {
      return new InertiaConfig(
          () -> "v1",
          "app",
          Set.of("Home", "Error"),
          RootView.minimal(),
          (p, r) -> {
            calls.incrementAndGet();
            return java.util.concurrent.CompletableFuture.completedFuture(
                new SsrGateway.Fallback("invalid-response"));
          },
          r -> Props.empty(),
          false,
          false);
    }

    @Bean
    InertiaErrorPage errorPage() {
      return (r, status) -> {
        errors.incrementAndGet();
        return new InertiaResponse("Error", Props.builder().put("status", status).build());
      };
    }
  }

  @Controller
  static class Pages {
    @GetMapping("/required")
    InertiaResponse required() {
      return new InertiaResponse("Home", Props.empty()).requireSsr();
    }

    @GetMapping("/optional")
    InertiaResponse optional() {
      return new InertiaResponse("Home", Props.empty());
    }
  }

  @RestController
  static class Api {
    @GetMapping("/api")
    String api() {
      return "ok";
    }
  }

  @Test
  void requiredHtmlIsSafe503AndDoesNotRetryRendererForErrorPage() throws Exception {
    calls.set(0);
    errors.set(0);
    var session = new org.springframework.mock.web.MockHttpSession();
    var codec = new PageCodec();
    var stored = codec.object();
    stored.set(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
    new io.inertia.spring.HttpSessionStore(session).merge(stored);
    mvc.perform(get("/required").session(session))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Error")));
    assertEquals(1, calls.get());
    assertEquals(1, errors.get());
    mvc.perform(
            get("/required")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flash.toast").value("keep"));
    mvc.perform(
            get("/required")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flash").doesNotExist());
    assertEquals(1, calls.get());
  }

  @Test
  void requiredJsonDoesNotDispatchSsrAndOptionalHtmlStillUsesCsr() throws Exception {
    calls.set(0);
    mvc.perform(get("/required").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.component").value("Home"));
    assertEquals(0, calls.get());
    mvc.perform(get("/optional"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page")));
    assertEquals(1, calls.get());
  }
}
