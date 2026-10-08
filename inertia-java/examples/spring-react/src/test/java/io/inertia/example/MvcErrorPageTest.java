package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = MvcErrorPageTest.TestApplication.class)
@AutoConfigureMockMvc
class MvcErrorPageTest {
  @Autowired MockMvc mvc;
  static final AtomicInteger attempts = new AtomicInteger();
  static final String SECRET = "SECRET_FAILURE_VALUE";

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({PageController.class, ApiController.class, Advice.class, SecurityConfiguration.class})
  static class TestApplication {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Home", "Error"));
    }

    @Bean
    InertiaErrorPage errorPage() {
      return (request, status) -> {
        attempts.incrementAndGet();
        var props = Props.builder().put("status", status);
        if (request.url().equals("/broken-error-page"))
          props.put(
              "failed",
              Prop.lazy(
                  () -> {
                    throw new IllegalStateException(SECRET);
                  }));
        return new InertiaResponse("Error", props.build()).status(200);
      };
    }
  }

  @Controller
  static class PageController {
    @GetMapping({"/explode", "/broken-error-page"})
    InertiaResponse explode() {
      throw new IllegalStateException(SECRET);
    }

    @GetMapping("/prop-failure")
    InertiaResponse fails() {
      return new InertiaResponse(
          "Home",
          Props.builder()
              .put(
                  "private",
                  Prop.lazy(
                      () -> {
                        throw new IllegalStateException(SECRET);
                      }))
              .build());
    }

    @GetMapping("/forbidden")
    InertiaResponse forbidden() {
      return new InertiaResponse(
          "Home",
          Props.builder()
              .put(
                  "private",
                  Prop.lazy(
                      () -> {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, SECRET);
                      }))
              .build());
    }

    @GetMapping("/missing-exception")
    InertiaResponse missing() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, SECRET);
    }

    @GetMapping("/gone")
    InertiaResponse gone() {
      throw new Gone();
    }

    @GetMapping("/number")
    InertiaResponse number(@RequestParam int value) {
      return new InertiaResponse("Home", Props.empty());
    }

    @GetMapping("/ok")
    InertiaResponse ok() {
      return new InertiaResponse("Home", Props.empty());
    }

    @GetMapping("/advised")
    InertiaResponse advised() {
      throw new Advised();
    }
  }

  @ResponseStatus(code = HttpStatus.GONE, reason = SECRET)
  static class Gone extends RuntimeException {}

  static class Advised extends RuntimeException {}

  @ControllerAdvice
  static class Advice {
    @ExceptionHandler(Advised.class)
    ResponseEntity<Map<String, String>> handles() {
      return ResponseEntity.status(409).body(Map.of("handled", "application"));
    }
  }

  @RestController
  static class ApiController {
    @GetMapping("/api/error")
    Map<String, String> error() {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "api");
    }
  }

  @Test
  void controllerAndPropsFailuresUseSafeJsonAndKeepSessionFlash() throws Exception {
    for (String path : List.of("/explode", "/prop-failure")) {
      var session = new MockHttpSession();
      new HttpSessionStore(session)
          .put(InertiaContext.FLASH, new PageCodec().value(Map.of("toast", "keep")));
      var result =
          mvc.perform(
                  get(path)
                      .session(session)
                      .header("X-Inertia", "true")
                      .header("X-Inertia-Version", "v1"))
              .andExpect(status().isInternalServerError())
              .andExpect(header().string("X-Inertia", "true"))
              .andExpect(header().string("Cache-Control", "private, no-store"))
              .andExpect(jsonPath("$.component").value("Error"))
              .andExpect(jsonPath("$.props.status").value(500))
              .andExpect(jsonPath("$.flash").doesNotExist())
              .andReturn();
      assertFalse(result.getResponse().getContentAsString().contains(SECRET));
      mvc.perform(
              get("/ok")
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1"))
          .andExpect(jsonPath("$.flash.toast").value("keep"));
    }
  }

  @Test
  void statusExceptionsAndBindingErrorsKeepTheirStatusWithoutReasons() throws Exception {
    for (var entry :
        Map.of("/forbidden", 403, "/gone", 410, "/missing-exception", 404, "/number?value=bad", 400)
            .entrySet()) {
      var result =
          mvc.perform(
                  get(entry.getKey()).header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
              .andExpect(status().is(entry.getValue()))
              .andExpect(jsonPath("$.props.status").value(entry.getValue()))
              .andReturn();
      assertFalse(result.getResponse().getContentAsString().contains(SECRET));
    }
    mvc.perform(get("/missing-exception"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("text/html"))
        .andExpect(header().doesNotExist("X-Inertia"));
  }

  @Test
  void failedErrorPageIsAttemptedOnceThenSafeText() throws Exception {
    attempts.set(0);
    mvc.perform(
            get("/broken-error-page").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith("text/plain"))
        .andExpect(content().string("Internal Server Error"))
        .andExpect(header().doesNotExist("X-Inertia"));
    assertEquals(1, attempts.get());
  }

  @Test
  void applicationAdviceAndRestRetainOrdinarySpringBehavior() throws Exception {
    attempts.set(0);
    mvc.perform(get("/advised").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.handled").value("application"))
        .andExpect(header().doesNotExist("X-Inertia"));
    mvc.perform(get("/api/error").header("X-Inertia", "true"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist("X-Inertia"));
    assertEquals(0, attempts.get());
  }
}
