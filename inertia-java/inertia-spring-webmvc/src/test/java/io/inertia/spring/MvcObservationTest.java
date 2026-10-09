package io.inertia.spring;

import static io.inertia.core.InertiaObserver.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.inertia.core.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class MvcObservationTest {
  @Controller
  static class Pages {
    final AtomicInteger calls = new AtomicInteger();

    @GetMapping("/page")
    public InertiaResponse page(InertiaContext context) {
      calls.incrementAndGet();
      return context.render("Home", Props.builder().put("secret", "props-secret").build());
    }

    @GetMapping("/redirect")
    public HttpOutcome redirect(InertiaContext context) {
      return context.flash("message", "flash-secret").back();
    }

    @GetMapping("/failed")
    public InertiaResponse failed(InertiaContext context) {
      return context.render(
          "Home",
          Props.builder()
              .put(
                  "secret",
                  Prop.lazy(
                      () -> {
                        throw new IllegalStateException("exception-secret");
                      }))
              .build());
    }

    @GetMapping("/rest")
    @ResponseBody
    public Map<String, String> rest() {
      return Map.of("hello", "world");
    }
  }

  static class Registry extends InterceptorRegistry {
    List<Object> handlers() {
      return getInterceptors();
    }
  }

  static class Fixture implements AutoCloseable {
    final PageCodec codec = new PageCodec();
    final List<Event> events = new CopyOnWriteArrayList<>();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Pages pages = new Pages();
    final ResponseRenderer renderer;
    final MockMvc mvc;

    Fixture(boolean errorPage) {
      var config = InertiaConfig.basic("v1", Set.of("Home", "Error"));
      var observer =
          InertiaObserver.combine(
              event -> {
                throw new IllegalStateException("logger failed");
              },
              events::add);
      var props =
          new PropsResolver(codec, executor, Duration.ofSeconds(1), 2, Clock.systemUTC(), observer);
      renderer = new ResponseRenderer(config, codec, props, observer, "primary");
      InertiaErrorPage errors =
          errorPage ? (request, status) -> new InertiaResponse("Error", Props.empty()) : null;
      var integration = new InertiaMvcConfigurer(config, renderer, Duration.ofSeconds(2), errors);
      var registry = new Registry();
      integration.addInterceptors(registry);
      var arguments = new ArrayList<HandlerMethodArgumentResolver>();
      integration.addArgumentResolvers(arguments);
      var returns = new ArrayList<HandlerMethodReturnValueHandler>();
      integration.addReturnValueHandlers(returns);
      mvc =
          MockMvcBuilders.standaloneSetup(pages)
              .setCustomArgumentResolvers(arguments.toArray(HandlerMethodArgumentResolver[]::new))
              .setCustomReturnValueHandlers(returns.toArray(HandlerMethodReturnValueHandler[]::new))
              .addInterceptors((HandlerInterceptor) registry.handlers().getFirst())
              .setHandlerExceptionResolvers(
                  new InertiaExceptionResolver(renderer, Duration.ofSeconds(2), errors))
              .build();
    }

    @Override
    public void close() {
      executor.shutdownNow();
    }

    void awaitEvents(int count) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (events.size() < count && System.nanoTime() < deadline) Thread.sleep(1);
      assertEquals(count, events.size());
    }
  }

  @Test
  void versionConflictIsObservedBeforeControllerAndDoesNotCreateSession() throws Exception {
    try (var fixture = new Fixture(true)) {
      var result =
          fixture
              .mvc
              .perform(
                  get("/page")
                      .queryParam("secret", "url-secret")
                      .header("X-Inertia", "true")
                      .header("X-Inertia-Version", "old")
                      .header("X-Request-Id", "untrusted-secret"))
              .andReturn();
      assertEquals(409, result.getResponse().getStatus());
      assertNull(result.getRequest().getSession(false));
      assertEquals(0, fixture.pages.calls.get());
      assertEquals(
          List.of(Operation.VERSION_CONFLICT, Operation.RESPONSE),
          fixture.events.stream().map(Event::operation).toList());
      assertEquals(Outcome.CONFLICT, fixture.events.getFirst().outcome());
      assertEquals(Reason.VERSION_MISMATCH, fixture.events.getFirst().reason());
      assertEquals(ResponseKind.LOCATION, fixture.events.getLast().response());
      assertEquals(fixture.events.getFirst().requestId(), fixture.events.getLast().requestId());
      assertFalse(fixture.codec.value(fixture.events).toString().contains("secret"));
    }
  }

  @Test
  void htmlJsonRedirectAndRestPreserveResponseSemantics() throws Exception {
    try (var fixture = new Fixture(true)) {
      assertEquals(200, fixture.mvc.perform(get("/page")).andReturn().getResponse().getStatus());
      assertEquals(
          200,
          fixture
              .mvc
              .perform(get("/page").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
              .andReturn()
              .getResponse()
              .getStatus());
      assertEquals(
          302, fixture.mvc.perform(get("/redirect")).andReturn().getResponse().getStatus());
      fixture.awaitEvents(13);
      var writes =
          fixture.events.stream().filter(e -> e.operation() == Operation.RESPONSE).toList();
      assertEquals(
          List.of(ResponseKind.HTML, ResponseKind.JSON, ResponseKind.REDIRECT),
          writes.stream().map(Event::response).toList());
      assertEquals(3, writes.stream().map(Event::requestId).distinct().count());
      assertTrue(fixture.events.stream().anyMatch(e -> e.operation() == Operation.SESSION_MERGE));
      int count = fixture.events.size();
      assertEquals(200, fixture.mvc.perform(get("/rest")).andReturn().getResponse().getStatus());
      assertEquals(count, fixture.events.size());
    }
  }

  @Test
  void failedPageAndSafeErrorPageShareCorrelationWhilePlaintextAlsoHasWriteEvent()
      throws Exception {
    try (var fixture = new Fixture(true)) {
      var result = fixture.mvc.perform(get("/failed")).andReturn();
      assertEquals(500, result.getResponse().getStatus());
      fixture.awaitEvents(8);
      assertTrue(fixture.events.stream().anyMatch(e -> e.operation() == Operation.SESSION_ABORT));
      var renders = fixture.events.stream().filter(e -> e.operation() == Operation.RENDER).toList();
      assertEquals(2, renders.size());
      assertEquals(
          Outcome.FAILURE,
          renders.stream()
              .filter(e -> e.component().equals("Home"))
              .findFirst()
              .orElseThrow()
              .outcome());
      assertEquals(
          Outcome.SUCCESS,
          renders.stream()
              .filter(e -> e.component().equals("Error"))
              .findFirst()
              .orElseThrow()
              .outcome());
      assertEquals(1, fixture.events.stream().map(Event::requestId).distinct().count());
      var write =
          fixture.events.stream()
              .filter(e -> e.operation() == Operation.RESPONSE)
              .findFirst()
              .orElseThrow();
      assertEquals(500, write.status());
      assertEquals(ResponseKind.HTML, write.response());
      assertFalse(fixture.codec.value(fixture.events).toString().contains("exception-secret"));
    }
    try (var fixture = new Fixture(false)) {
      var result = fixture.mvc.perform(get("/failed")).andReturn();
      assertEquals("Internal Server Error", result.getResponse().getContentAsString());
      fixture.awaitEvents(5);
      var write =
          fixture.events.stream()
              .filter(e -> e.operation() == Operation.RESPONSE)
              .findFirst()
              .orElseThrow();
      assertEquals(500, write.status());
      assertEquals(ResponseKind.OTHER, write.response());
    }
  }

  @Test
  void writerFailureIsReportedWithoutExposingCauseAndInvalidSnapshotStillWritesPlaintext()
      throws Exception {
    var events = new ArrayList<Event>();
    var request = new MockHttpServletRequest("GET", "/page");
    var failure = new IOException("transport-secret");
    var broken =
        new HttpServletResponseWrapper(new MockHttpServletResponse()) {
          @Override
          public PrintWriter getWriter() throws IOException {
            throw failure;
          }
        };
    assertSame(
        failure,
        assertThrows(
            IOException.class,
            () ->
                InertiaMvcConfigurer.writeObserved(
                    request, broken, HttpOutcome.empty(200), events::add, "Home")));
    assertEquals(1, events.size());
    assertEquals(Outcome.FAILURE, events.getFirst().outcome());
    assertFalse(new PageCodec().value(events).toString().contains("transport-secret"));
    request.addHeader("bad", "x".repeat(8193));
    var safe = new MockHttpServletResponse();
    InertiaMvcConfigurer.writeObserved(
        request, safe, new HttpOutcome(500, Map.of(), "Internal Server Error"), events::add, "");
    // Reuse cached valid snapshots normally; exercise a fresh invalid snapshot separately.
    var invalid = new MockHttpServletRequest("GET", "/page");
    invalid.addHeader("bad", "x".repeat(8193));
    var fallback = new MockHttpServletResponse();
    InertiaMvcConfigurer.writeObserved(
        invalid,
        fallback,
        new HttpOutcome(500, Map.of(), "Internal Server Error"),
        events::add,
        "");
    assertEquals(500, fallback.getStatus());
    assertEquals("Internal Server Error", fallback.getContentAsString());
    assertEquals(2, events.size());
  }
}
