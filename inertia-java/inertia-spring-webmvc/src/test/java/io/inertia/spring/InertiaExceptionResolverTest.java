package io.inertia.spring;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.method.HandlerMethod;

class InertiaExceptionResolverTest {
  static class Controller {
    public InertiaResponse page() {
      return null;
    }

    public Map<String, String> rest() {
      return Map.of();
    }
  }

  @Test
  void committedResponsesAndOrdinaryRestAreNotRewritten() throws Exception {
    var resolver = new InertiaExceptionResolver(null, Duration.ofSeconds(1), null);
    var request = new MockHttpServletRequest("GET", "/page");
    var response = new MockHttpServletResponse();
    response.getWriter().write("already written");
    response.flushBuffer();
    assertNull(
        resolver.resolveException(
            request,
            response,
            new HandlerMethod(new Controller(), Controller.class.getMethod("page")),
            new IllegalStateException()));
    assertEquals("already written", response.getContentAsString());
    var rest = new MockHttpServletResponse();
    assertNull(
        resolver.resolveException(
            request,
            rest,
            new HandlerMethod(new Controller(), Controller.class.getMethod("rest")),
            new IllegalStateException()));
    assertEquals(200, rest.getStatus());
    assertEquals("", rest.getContentAsString());
  }

  @Test
  void reentryDoesNotAttemptErrorPageTwiceAndKeepsSecurityHeaders() throws Exception {
    var codec = new PageCodec();
    var attempts = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              InertiaConfig.basic("v1", Set.of("Error")),
              codec,
              new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var resolver =
          new InertiaExceptionResolver(
              renderer,
              Duration.ofSeconds(1),
              (request, status) -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("SECRET");
              });
      var request = new MockHttpServletRequest("GET", "/page");
      var response = new MockHttpServletResponse();
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader("X-Inertia", "true");
      var handler = new HandlerMethod(new Controller(), Controller.class.getMethod("page"));
      assertNotNull(
          resolver.resolveException(
              request, response, handler, new IllegalStateException("SECRET")));
      assertNotNull(
          resolver.resolveException(
              request, response, handler, new IllegalStateException("SECRET")));
      assertEquals(1, attempts.get());
      assertEquals(500, response.getStatus());
      assertEquals("Internal Server Error", response.getContentAsString());
      assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
      assertNull(response.getHeader("X-Inertia"));
    }
  }

  @Test
  void errorPageTimeoutAndLateSsrCannotWriteAgain() throws Exception {
    var codec = new PageCodec();
    var pendingSsr = new CompletableFuture<SsrGateway.Result>();
    var calls = new AtomicInteger();
    var config =
        new InertiaConfig(
            () -> "v1",
            "app",
            Set.of("Error"),
            RootView.minimal(),
            (page, request) -> {
              calls.incrementAndGet();
              return pendingSsr;
            },
            request -> Props.empty(),
            false,
            false);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var resolver =
          new InertiaExceptionResolver(
              renderer,
              Duration.ofMillis(20),
              (request, status) -> new InertiaResponse("Error", Props.empty()));
      var response = new MockHttpServletResponse();
      var handler = new HandlerMethod(new Controller(), Controller.class.getMethod("page"));
      assertNotNull(
          resolver.resolveException(
              new MockHttpServletRequest("GET", "/page"),
              response,
              handler,
              new IllegalStateException()));
      assertEquals(500, response.getStatus());
      assertEquals("Internal Server Error", response.getContentAsString());
      pendingSsr.complete(new SsrGateway.Rendered("", "late body"));
      assertEquals("Internal Server Error", response.getContentAsString());
      assertEquals(1, calls.get());
    }
  }

  @Test
  void errorTemplateFailureFallsBackWithoutRecursion() throws Exception {
    var codec = new PageCodec();
    var calls = new AtomicInteger();
    var config =
        new InertiaConfig(
            () -> "v1",
            "app",
            Set.of("Error"),
            view -> {
              calls.incrementAndGet();
              throw new IllegalStateException("SECRET");
            },
            null,
            request -> Props.empty(),
            false,
            false);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var resolver =
          new InertiaExceptionResolver(
              renderer,
              Duration.ofSeconds(1),
              (request, status) -> new InertiaResponse("Error", Props.empty()));
      var response = new MockHttpServletResponse();
      assertNotNull(
          resolver.resolveException(
              new MockHttpServletRequest("GET", "/page"),
              response,
              new HandlerMethod(new Controller(), Controller.class.getMethod("page")),
              new IllegalStateException()));
      assertEquals("Internal Server Error", response.getContentAsString());
      assertEquals(1, calls.get());
    }
  }
}
