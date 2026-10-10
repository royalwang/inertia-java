package io.inertia.spring;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class InertiaRequestLifecycleTest {
  static class Controller {
    public InertiaResponse page(InertiaContext context, InertiaRequest request) {
      return null;
    }
  }

  static class Registry extends InterceptorRegistry {
    List<Object> handlers() {
      return getInterceptors();
    }
  }

  @Test
  void reentryReusesImmutableSnapshotAndPendingEffects() throws Exception {
    var codec = new PageCodec();
    var config = InertiaConfig.basic("v1", Set.of("Home"));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var integration = new InertiaMvcConfigurer(config, renderer, Duration.ofSeconds(1));
      var registry = new Registry();
      integration.addInterceptors(registry);
      var interceptor = (HandlerInterceptor) registry.handlers().getFirst();
      var handler =
          new HandlerMethod(
              new Controller(),
              Controller.class.getMethod("page", InertiaContext.class, InertiaRequest.class));
      var request = new MockHttpServletRequest("GET", "/page");
      request.addHeader("X-Inertia", "true");
      request.addHeader("X-Inertia-Version", "v1");
      var response = new MockHttpServletResponse();
      var resolvers = new ArrayList<HandlerMethodArgumentResolver>();
      integration.addArgumentResolvers(resolvers);
      var arguments = resolvers.getFirst();
      var web = new ServletWebRequest(request, response);
      assertTrue(interceptor.preHandle(request, response, handler));
      var first =
          (InertiaContext)
              arguments.resolveArgument(
                  handler.getMethodParameters()[0], new ModelAndViewContainer(), web, null);
      var snapshot =
          arguments.resolveArgument(
              handler.getMethodParameters()[1], new ModelAndViewContainer(), web, null);
      first.share("queued", 42);
      request.setRequestURI("/internal-forward");
      assertTrue(interceptor.preHandle(request, response, handler));
      var second =
          (InertiaContext)
              arguments.resolveArgument(
                  handler.getMethodParameters()[0], new ModelAndViewContainer(), web, null);
      assertSame(first, second);
      assertSame(
          snapshot,
          arguments.resolveArgument(
              handler.getMethodParameters()[1], new ModelAndViewContainer(), web, null));
      var outcome =
          renderer
              .render(second, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get();
      assertEquals(42, codec.read(outcome.body()).at("/props/queued").asInt());
      assertEquals("/page", codec.read(outcome.body()).path("url").asText());
      assertThrows(IllegalStateException.class, () -> second.share("late", 1));
    }
  }

  @Test
  void customStoreFactoryIsRequestOwnedAndVersionConflictDoesNotAttachStorage() throws Exception {
    var codec = new PageCodec();
    var config = InertiaConfig.basic("v1", Set.of("Home"));
    var memory = new MemorySessionStore();
    memory.put(InertiaContext.FLASH, codec.value(Map.of("toast", "custom")));
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 2));
      var integration =
          new InertiaMvcConfigurer(
              config,
              renderer,
              Duration.ofSeconds(1),
              null,
              "test",
              codec,
              request -> {
                calls.incrementAndGet();
                return memory;
              });
      var registry = new Registry();
      integration.addInterceptors(registry);
      var interceptor = (HandlerInterceptor) registry.handlers().getFirst();
      var handler =
          new HandlerMethod(
              new Controller(),
              Controller.class.getMethod("page", InertiaContext.class, InertiaRequest.class));
      var old = new MockHttpServletRequest("GET", "/page");
      old.addHeader("X-Inertia", "true");
      old.addHeader("X-Inertia-Version", "old");
      var conflict = new MockHttpServletResponse();
      assertFalse(interceptor.preHandle(old, conflict, handler));
      assertEquals(409, conflict.getStatus());
      assertEquals(0, calls.get());
      assertNull(old.getSession(false));
      var request = new MockHttpServletRequest("GET", "/page");
      request.addHeader("X-Inertia", "true");
      request.addHeader("X-Inertia-Version", "v1");
      var response = new MockHttpServletResponse();
      assertTrue(interceptor.preHandle(request, response, handler));
      assertTrue(interceptor.preHandle(request, response, handler));
      assertEquals(1, calls.get());
      var resolvers = new ArrayList<HandlerMethodArgumentResolver>();
      integration.addArgumentResolvers(resolvers);
      var context =
          (InertiaContext)
              resolvers
                  .getFirst()
                  .resolveArgument(
                      handler.getMethodParameters()[0],
                      new ModelAndViewContainer(),
                      new ServletWebRequest(request, response),
                      null);
      var outcome =
          renderer
              .render(context, new InertiaResponse("Home", Props.empty()))
              .toCompletableFuture()
              .get();
      assertEquals("custom", codec.read(outcome.body()).at("/flash/toast").asText());
      assertNull(memory.get(InertiaContext.FLASH));
      assertNull(request.getSession(false));
    }
  }

  @Test
  void nonceComesOnlyFromServerAttributeAndSnapshotIsImmutable() {
    var request = new MockHttpServletRequest("GET", "/");
    request.addHeader("X-CSP-Nonce", "client_supplied_0123456789");
    assertNull(InertiaMvcConfigurer.snapshot(request).nonce());
    var trusted = new MockHttpServletRequest("GET", "/");
    trusted.setAttribute(InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE, "server_nonce_0123456789");
    var snapshot = InertiaMvcConfigurer.snapshot(trusted);
    trusted.setAttribute(InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE, "changed_nonce_0123456789");
    assertEquals("server_nonce_0123456789", InertiaMvcConfigurer.snapshot(trusted).nonce());
    assertSame(snapshot, InertiaMvcConfigurer.snapshot(trusted));
    var bad = new MockHttpServletRequest("GET", "/");
    bad.setAttribute(InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE, "\" injection");
    assertThrows(IllegalArgumentException.class, () -> InertiaMvcConfigurer.snapshot(bad));
  }
}
