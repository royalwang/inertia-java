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

@SpringBootTest(
    classes = MvcConfigPresentationTest.App.class,
    properties = "inertia.all-errors=true")
@AutoConfigureMockMvc
class MvcConfigPresentationTest {
  @Autowired MockMvc mvc;
  static final AtomicInteger urls = new AtomicInteger();
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
              null,
              r -> Props.builder().put("app", "Application").build(),
              false,
              false)
          .withSharedPropKeys(false)
          .withUrlResolver(
              r -> {
                urls.incrementAndGet();
                if (r.url().startsWith("/invalid"))
                  throw new IllegalStateException("SECRET_CALLBACK");
                return "/public" + r.fullUrl().getRawPath();
              });
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
    @GetMapping("/ok")
    InertiaResponse ok(InertiaContext context) {
      context.withErrors(Map.of("name", List.of("Required", "Too short")));
      return new InertiaResponse("Home", Props.empty());
    }

    @GetMapping({"/bad", "/invalid"})
    InertiaResponse bad() {
      return new InertiaResponse(
          "Home",
          Props.builder()
              .put(
                  "failure",
                  Prop.lazy(
                      () -> {
                        throw new IllegalStateException("SECRET_DATA");
                      }))
              .build());
    }
  }

  @RestController
  static class Api {
    @GetMapping("/api/data")
    Map<String, Boolean> data() {
      return Map.of("ok", true);
    }
  }

  @Test
  void bootAllErrorsOverrideKeepsUrlAndExposureSettings() throws Exception {
    urls.set(0);
    mvc.perform(
            get("/ok?token=secret").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/public/ok"))
        .andExpect(jsonPath("$.sharedProps").doesNotExist())
        .andExpect(jsonPath("$.props.app").value("Application"))
        .andExpect(jsonPath("$.props.errors.name.length()").value(2));
    assertEquals(1, urls.get());
  }

  @Test
  void safeErrorPageUsesSamePresentationPolicyAndBrokenResolverFallsBackOnce() throws Exception {
    urls.set(0);
    errors.set(0);
    var response =
        mvc.perform(
                get("/bad?token=secret")
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.component").value("Error"))
            .andExpect(jsonPath("$.url").value("/public/bad"))
            .andExpect(jsonPath("$.sharedProps").doesNotExist())
            .andReturn()
            .getResponse();
    assertFalse(response.getContentAsString().contains("SECRET"));
    assertEquals(2, urls.get());
    assertEquals(1, errors.get());
    urls.set(0);
    errors.set(0);
    response =
        mvc.perform(get("/invalid").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andReturn()
            .getResponse();
    assertEquals(2, urls.get());
    assertEquals(1, errors.get());
    assertFalse(response.getContentAsString().contains("SECRET"));
  }

  @Test
  void versionConflictAndRestDoNotRunPresentationCallbacks() throws Exception {
    urls.set(0);
    mvc.perform(
            get("/ok?token=secret").header("X-Inertia", "true").header("X-Inertia-Version", "old"))
        .andExpect(status().isConflict())
        .andExpect(header().string("X-Inertia-Location", "http://localhost/ok?token=secret"));
    mvc.perform(get("/api/data").header("X-Inertia", "true").header("X-Inertia-Version", "old"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
    assertEquals(0, urls.get());
  }
}
