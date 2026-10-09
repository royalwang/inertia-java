package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(classes = MvcAdviceContractTest.App.class)
@AutoConfigureMockMvc
class MvcAdviceContractTest {
  @Autowired MockMvc mvc;
  static final AtomicInteger adviceCalls = new AtomicInteger();
  static final AtomicInteger errorCalls = new AtomicInteger();

  static class Failure extends RuntimeException {}

  static class BrokenAdvice extends RuntimeException {}

  static class RestFailure extends RuntimeException {}

  static class LocalFailure extends RuntimeException {}

  static class PlainFailure extends RuntimeException {}

  static class GenericFailure extends RuntimeException {}

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({
    Pages.class,
    Api.class,
    Advice.class,
    ApiAdvice.class,
    GenericAdvice.class,
    SecurityConfiguration.class
  })
  static class App {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Home", "Error"));
    }

    @Bean
    InertiaErrorPage errors() {
      return (r, s) -> {
        errorCalls.incrementAndGet();
        return new InertiaResponse("Error", Props.builder().put("status", s).build());
      };
    }
  }

  @Controller
  static class Pages {
    @GetMapping("/controller-advice")
    InertiaResponse direct() {
      throw new Failure();
    }

    @GetMapping("/props-advice")
    InertiaResponse props() {
      return new InertiaResponse(
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

    @GetMapping("/broken-advice")
    InertiaResponse broken() {
      throw new BrokenAdvice();
    }

    @GetMapping("/local-advice")
    InertiaResponse local() {
      throw new LocalFailure();
    }

    @ExceptionHandler(LocalFailure.class)
    InertiaResponse localHandler() {
      return new InertiaResponse("Home", Props.builder().put("owner", "local").build()).status(409);
    }

    @GetMapping("/plain-advice")
    InertiaResponse plain() {
      return new InertiaResponse(
          "Home",
          Props.builder()
              .put(
                  "failed",
                  Prop.lazy(
                      () -> {
                        throw new PlainFailure();
                      }))
              .build());
    }

    @GetMapping("/generic-advice")
    InertiaResponse generic() {
      throw new GenericFailure();
    }

    @GetMapping("/ok")
    InertiaResponse ok() {
      return new InertiaResponse("Home", Props.empty());
    }
  }

  @ControllerAdvice(assignableTypes = Pages.class)
  static class Advice {
    @ExceptionHandler(Failure.class)
    InertiaResponse handle(InertiaContext context) {
      adviceCalls.incrementAndGet();
      context.share("advice", "handled");
      return new InertiaResponse("Home", Props.builder().put("origin", "application").build())
          .status(418);
    }

    @ExceptionHandler(PlainFailure.class)
    ResponseEntity<Map<String, String>> plain() {
      return ResponseEntity.status(409).body(Map.of("owner", "plain"));
    }

    @ExceptionHandler(BrokenAdvice.class)
    InertiaResponse broken() {
      adviceCalls.incrementAndGet();
      return new InertiaResponse(
          "Home",
          Props.builder()
              .put(
                  "again",
                  Prop.lazy(
                      () -> {
                        throw new IllegalStateException("SECRET_ADVICE");
                      }))
              .build());
    }
  }

  @RestController
  static class Api {
    @GetMapping("/api/advice")
    Map<String, String> fail() {
      throw new RestFailure();
    }
  }

  @RestControllerAdvice(assignableTypes = Api.class)
  static class ApiAdvice {
    @ExceptionHandler(RestFailure.class)
    ResponseEntity<Map<String, String>> handle() {
      return ResponseEntity.status(422).body(Map.of("owner", "rest"));
    }
  }

  abstract static class GenericAdviceBase<T> {
    abstract T response(InertiaContext context);

    @ExceptionHandler(GenericFailure.class)
    T handleGeneric(InertiaContext context) {
      return response(context);
    }
  }

  @ControllerAdvice(assignableTypes = Pages.class)
  static class GenericAdvice extends GenericAdviceBase<InertiaResponse> {
    @Override
    InertiaResponse response(InertiaContext context) {
      context.share("owner", "generic");
      return new InertiaResponse("Home", Props.empty()).status(418);
    }
  }

  @Test
  void inheritedGenericAdviceProtectsSessionDelivery() throws Exception {
    var session = new MockHttpSession();
    new HttpSessionStore(session)
        .put(InertiaContext.FLASH, new PageCodec().value(Map.of("toast", "keep")));
    mvc.perform(
            get("/generic-advice")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(status().is(418))
        .andExpect(jsonPath("$.props.owner").value("generic"))
        .andExpect(jsonPath("$.flash").doesNotExist());
    mvc.perform(
            get("/ok")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.flash.toast").value("keep"));
    assertEquals(0, errorCalls.get());
  }

  @BeforeEach
  void reset() {
    adviceCalls.set(0);
    errorCalls.set(0);
  }

  @Test
  void typedAdviceHandlesControllerAndAsyncPropFailuresAndPreservesReservedFlash()
      throws Exception {
    for (String path : List.of("/controller-advice", "/props-advice")) {
      var session = new MockHttpSession();
      new HttpSessionStore(session)
          .put(InertiaContext.FLASH, new PageCodec().value(Map.of("toast", "keep")));
      mvc.perform(
              get(path)
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1"))
          .andExpect(status().is(418))
          .andExpect(header().string("X-Inertia", "true"))
          .andExpect(jsonPath("$.props.origin").value("application"))
          .andExpect(jsonPath("$.props.advice").value("handled"))
          .andExpect(jsonPath("$.flash").doesNotExist());
      mvc.perform(
              get("/ok")
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1"))
          .andExpect(jsonPath("$.flash.toast").value("keep"));
    }
    assertEquals(2, adviceCalls.get());
    assertEquals(0, errorCalls.get());
  }

  @Test
  void failedAdviceFallsThroughToOneSafeLibraryErrorPage() throws Exception {
    var response =
        mvc.perform(
                get("/broken-advice").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.component").value("Error"))
            .andReturn();
    assertFalse(response.getResponse().getContentAsString().contains("SECRET_ADVICE"));
    assertEquals(1, adviceCalls.get());
    assertEquals(1, errorCalls.get());
  }

  @Test
  void restAdviceStaysOutsidePageProtocol() throws Exception {
    mvc.perform(get("/api/advice").header("X-Inertia", "true"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.owner").value("rest"))
        .andExpect(header().doesNotExist("X-Inertia"))
        .andExpect(header().doesNotExist("Vary"));
    assertEquals(0, adviceCalls.get());
    assertEquals(0, errorCalls.get());
  }

  @Test
  void localTypedHandlerAndPlainAdviceBothPreserveOriginalSessionDelivery() throws Exception {
    for (String path : List.of("/local-advice", "/plain-advice")) {
      var session = new MockHttpSession();
      new HttpSessionStore(session)
          .put(InertiaContext.FLASH, new PageCodec().value(Map.of("toast", "keep")));
      var result =
          mvc.perform(
                  get(path)
                      .session(session)
                      .header("X-Inertia", "true")
                      .header("X-Inertia-Version", "v1"))
              .andExpect(status().isConflict());
      if (path.equals("/local-advice"))
        result
            .andExpect(jsonPath("$.props.owner").value("local"))
            .andExpect(jsonPath("$.flash").doesNotExist())
            .andExpect(header().string("X-Inertia", "true"));
      else
        result
            .andExpect(jsonPath("$.owner").value("plain"))
            .andExpect(header().doesNotExist("X-Inertia"));
      mvc.perform(
              get("/ok")
                  .session(session)
                  .header("X-Inertia", "true")
                  .header("X-Inertia-Version", "v1"))
          .andExpect(jsonPath("$.flash.toast").value("keep"));
    }
    var session = new MockHttpSession();
    new HttpSessionStore(session)
        .put(InertiaContext.FLASH, new PageCodec().value(Map.of("toast", "keep")));
    var html =
        mvc.perform(get("/local-advice").session(session))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(html.contains("data-page"));
    assertFalse(html.contains("keep"));
    mvc.perform(
            get("/ok")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.flash.toast").value("keep"));
    assertEquals(0, errorCalls.get());
  }
}
