package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(
    classes = MvcOutcomeAdviceContractTest.App.class,
    properties = "inertia.session-namespace=portal")
@AutoConfigureMockMvc
class MvcOutcomeAdviceContractTest {
  @Autowired MockMvc mvc;

  static class Failure extends RuntimeException {}

  static class LocalFailure extends RuntimeException {}

  static class InvalidFailure extends RuntimeException {}

  static class GenericFailure extends RuntimeException {}

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({Pages.class, Advice.class, GenericAdvice.class, SecurityConfiguration.class})
  static class App {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Home", "Error"));
    }

    @Bean
    InertiaErrorPage errorPage() {
      return (r, s) -> new InertiaResponse("Error", Props.builder().put("status", s).build());
    }
  }

  @Controller
  static class Pages {
    @GetMapping("/outcome/direct")
    InertiaResponse direct(InertiaContext context) {
      context.flash("discarded", "failed-controller");
      throw new Failure();
    }

    @GetMapping("/outcome/props")
    InertiaResponse props(InertiaContext context) {
      context.flash("discarded", "failed-props");
      return context.render(
          "Home",
          Props.builder()
              .put(
                  "failed",
                  Prop.lazy(
                      () -> {
                        throw new Failure();
                      }))
              .build());
    }

    @GetMapping("/outcome/local")
    InertiaResponse local(InertiaContext context) {
      context.flash("discarded", "failed-local");
      return context.render(
          "Home",
          Props.builder()
              .put(
                  "failed",
                  Prop.lazy(
                      () -> {
                        throw new LocalFailure();
                      }))
              .build());
    }

    @ExceptionHandler(LocalFailure.class)
    HttpOutcome localHandler() {
      return ProtocolPolicy.redirect("/outcome/ok");
    }

    @PutMapping("/outcome/put")
    InertiaResponse put(InertiaContext context) {
      return direct(context);
    }

    @GetMapping("/outcome/generic")
    InertiaResponse generic(InertiaContext context) {
      context.flash("discarded", "failed-generic");
      throw new GenericFailure();
    }

    @GetMapping("/outcome/detached")
    InertiaResponse detached(HttpServletRequest request) {
      request.getSession().removeAttribute("io.inertia.session.state.portal");
      throw new InvalidFailure();
    }

    @GetMapping("/outcome/invalid")
    InertiaResponse invalid(HttpServletRequest request) {
      request.getSession().invalidate();
      throw new InvalidFailure();
    }

    @GetMapping("/outcome/ok")
    InertiaResponse ok() {
      return new InertiaResponse("Home", Props.empty());
    }
  }

  @ControllerAdvice(assignableTypes = Pages.class)
  static class Advice {
    @ExceptionHandler({Failure.class, InvalidFailure.class})
    HttpOutcome redirect(InertiaContext context) {
      context.flash("advice", "recovered");
      context.withErrors(Map.of("form", "retry"));
      return context.back();
    }
  }

  abstract static class GenericAdviceBase<T> {
    abstract T response(InertiaContext context);

    @ExceptionHandler(GenericFailure.class)
    T generic(InertiaContext context) {
      return response(context);
    }
  }

  @ControllerAdvice(assignableTypes = Pages.class)
  static class GenericAdvice extends GenericAdviceBase<HttpOutcome> {
    @Override
    HttpOutcome response(InertiaContext context) {
      context.flash("advice", "recovered");
      return context.backWithErrors(Map.of("form", "retry"));
    }
  }

  MockHttpSession seeded() {
    var session = new MockHttpSession();
    new HttpSessionStore(session, "portal")
        .put(InertiaContext.FLASH, new PageCodec().value(Map.of("original", "keep")));
    new HttpSessionStore(session)
        .put(InertiaContext.FLASH, new PageCodec().value(Map.of("other", "isolated")));
    return session;
  }

  @Test
  void globalRedirectAdviceHasFreshEffectsAndRestoresReservedDelivery() throws Exception {
    for (String path : List.of("/outcome/direct", "/outcome/props", "/outcome/generic")) {
      var session = seeded();
      mvc.perform(
              get(path)
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1")
                  .header("Referer", "http://localhost/outcome/ok"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", "/outcome/ok"))
          .andExpect(header().string("Vary", "X-Inertia"));
      mvc.perform(
              get("/outcome/ok")
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.flash.original").value("keep"))
          .andExpect(jsonPath("$.flash.advice").value("recovered"))
          .andExpect(jsonPath("$.flash.discarded").doesNotExist())
          .andExpect(jsonPath("$.props.errors.form").value("retry"));
      mvc.perform(
              get("/outcome/ok")
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1"))
          .andExpect(jsonPath("$.flash").doesNotExist())
          .andExpect(jsonPath("$.props.errors.form").doesNotExist());
      assertEquals(
          "isolated",
          new HttpSessionStore(session).get(InertiaContext.FLASH).get("other").asText());
    }
  }

  @Test
  void localRedirectWithoutContextAlsoRestoresDelivery() throws Exception {
    var session = seeded();
    mvc.perform(
            get("/outcome/local")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/outcome/ok"));
    mvc.perform(
            get("/outcome/ok")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.flash.original").value("keep"))
        .andExpect(jsonPath("$.flash.discarded").doesNotExist());
  }

  @Test
  void unsafeRequestAdviceApplies303BeforeWriting() throws Exception {
    mvc.perform(
            put("/outcome/put")
                .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "advice-test-token"))
                .header("X-XSRF-TOKEN", "advice-test-token")
                .session(seeded())
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1")
                .header("Referer", "http://localhost/outcome/ok"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/outcome/ok"))
        .andExpect(header().string("Vary", "X-Inertia"));
  }

  @Test
  void detachedNamespaceCannotBeReattachedByAdvice() throws Exception {
    var session = seeded();
    mvc.perform(
            get("/outcome/detached")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().isInternalServerError())
        .andExpect(header().doesNotExist("Location"))
        .andExpect(jsonPath("$.component").value("Error"));
    assertNull(session.getAttribute("io.inertia.session.state.portal"));
    assertEquals(
        "isolated", new HttpSessionStore(session).get(InertiaContext.FLASH).get("other").asText());
  }

  @Test
  void invalidatedOriginalSessionCannotBeReplacedByAdvice() throws Exception {
    var result =
        mvc.perform(
                get("/outcome/invalid")
                    .session(seeded())
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1")
                    .header("Referer", "http://localhost/outcome/ok"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.component").value("Error"))
            .andReturn();
    assertNull(result.getRequest().getSession(false));
  }
}
