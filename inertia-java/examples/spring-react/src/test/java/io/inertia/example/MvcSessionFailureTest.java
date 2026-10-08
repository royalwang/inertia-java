package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootTest(
    classes = MvcSessionFailureTest.TestApplication.class,
    properties = "inertia.session-namespace=portal")
@AutoConfigureMockMvc
class MvcSessionFailureTest {
  @Autowired MockMvc mvc;
  static final AtomicReference<InertiaContext> latest = new AtomicReference<>();
  final PageCodec codec = new PageCodec();

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({Pages.class, SecurityConfiguration.class})
  static class TestApplication {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Home", "Error"));
    }

    @Bean
    InertiaErrorPage errorPage() {
      return (request, status) ->
          new InertiaResponse("Error", Props.builder().put("status", status).build());
    }
  }

  @Controller
  static class Pages {
    @GetMapping("/ok")
    InertiaResponse ok() {
      return new InertiaResponse("Home", Props.empty());
    }

    @GetMapping("/before")
    InertiaResponse before(InertiaContext context, HttpSession session) {
      latest.set(context);
      session.invalidate();
      return context.render("Home", Props.empty());
    }

    @GetMapping("/after")
    InertiaResponse after(InertiaContext context, HttpSession session) {
      latest.set(context);
      return context.render(
          "Home",
          Props.builder()
              .put(
                  "invalidate",
                  Prop.lazy(
                      () -> {
                        session.invalidate();
                        return "must not return success";
                      }))
              .build());
    }

    @GetMapping("/redirect")
    HttpOutcome redirect(InertiaContext context, HttpSession session) {
      latest.set(context);
      context.flash("toast", "unsaved pending");
      session.invalidate();
      return context.back();
    }
  }

  @Test
  void configuredNamespaceIsUsedByMvcAndLeavesDefaultNamespaceUntouched() throws Exception {
    var session = new MockHttpSession();
    new HttpSessionStore(session)
        .put(InertiaContext.FLASH, codec.value(Map.of("toast", "default")));
    new HttpSessionStore(session, "portal")
        .put(InertiaContext.FLASH, codec.value(Map.of("toast", "portal")));
    mvc.perform(
            get("/ok")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flash.toast").value("portal"));
    assertEquals(
        "default", new HttpSessionStore(session).get(InertiaContext.FLASH).path("toast").asText());
    assertNull(new HttpSessionStore(session, "portal").get(InertiaContext.FLASH));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/before", "/after", "/redirect"})
  void invalidationNeverAcknowledgesPageOrRedirectSuccessAndNeverLeaksReservedData(String path)
      throws Exception {
    var session = new MockHttpSession();
    new HttpSessionStore(session, "portal")
        .put(InertiaContext.FLASH, codec.value(Map.of("toast", "SECRET_RESERVED_FLASH")));
    var response =
        mvc.perform(
                get(path)
                    .session(session)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.component").value("Error"))
            .andExpect(jsonPath("$.props.status").value(500))
            .andExpect(jsonPath("$.flash").doesNotExist())
            .andExpect(header().doesNotExist("Location"))
            .andReturn()
            .getResponse();
    assertFalse(response.getContentAsString().contains("SECRET_RESERVED_FLASH"));
    assertFalse(response.getContentAsString().contains("must not return success"));
    assertThrows(IllegalStateException.class, () -> latest.get().flash("late", 1));
    mvc.perform(
            get("/ok")
                .session(new MockHttpSession())
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flash").doesNotExist());
  }

  @Test
  void sessionInitializationWriteFailureReturnsSafeFailure() throws Exception {
    var session =
        new MockHttpSession() {
          @Override
          public void setAttribute(String key, Object value) {
            if (key.startsWith("io.inertia.session.state"))
              throw new IllegalStateException("SECRET_STORAGE_FAILURE");
            super.setAttribute(key, value);
          }
        };
    var response =
        mvc.perform(
                get("/ok")
                    .session(session)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.component").value("Error"))
            .andReturn()
            .getResponse();
    assertFalse(response.getContentAsString().contains("SECRET_STORAGE_FAILURE"));
  }
}
