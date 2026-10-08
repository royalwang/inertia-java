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
}
