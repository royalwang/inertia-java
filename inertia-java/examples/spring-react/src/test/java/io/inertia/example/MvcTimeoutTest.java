package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootTest(classes = MvcTimeoutTest.TestApplication.class)
@AutoConfigureMockMvc
class MvcTimeoutTest {
  @Autowired MockMvc mvc;
  static final CompletableFuture<Object> slow = new CompletableFuture<>();
  static final AtomicReference<InertiaContext> current = new AtomicReference<>();

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @org.springframework.context.annotation.Import(SecurityConfiguration.class)
  static class TestApplication {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Home"));
    }

    @Bean
    PageController pages() {
      return new PageController();
    }

    @Bean
    InertiaMvcConfigurer mvcConfigurer(InertiaConfig config, ResponseRenderer renderer) {
      return new InertiaMvcConfigurer(config, renderer, Duration.ofSeconds(1));
    }
  }

  @Controller
  static class PageController {
    @GetMapping("/slow")
    InertiaResponse slow(InertiaContext context) {
      current.set(context);
      return context.render("Home", Props.builder().put("slow", Prop.async(() -> slow)).build());
    }

    @GetMapping("/ok")
    InertiaResponse ok() {
      return new InertiaResponse("Home", Props.empty());
    }
  }

  @Test
  void mvcTimeoutRestoresSessionAndRejectsLateEffects() throws Exception {
    var session = new MockHttpSession();
    new HttpSessionStore(session)
        .put(InertiaContext.FLASH, new PageCodec().value(Map.of("toast", "keep")));
    mvc.perform(get("/slow").session(session))
        .andExpect(status().isInternalServerError())
        .andExpect(content().string("Internal Server Error"));
    assertThrows(IllegalStateException.class, () -> current.get().flash("late", "discard"));
    assertTrue(slow.isCancelled(), "MVC deadline must cancel the original async prop source");
    assertFalse(slow.complete("late"));
    mvc.perform(
            get("/ok")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flash.toast").value("keep"));
  }
}
