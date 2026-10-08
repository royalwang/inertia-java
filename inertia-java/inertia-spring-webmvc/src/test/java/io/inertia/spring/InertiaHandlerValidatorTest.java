package io.inertia.spring;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.*;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class InertiaHandlerValidatorTest {
  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class Mvc {
    @Bean
    InertiaHandlerValidator validator(
        org.springframework.beans.factory.ObjectProvider<RequestMappingHandlerMapping> mappings) {
      return new InertiaHandlerValidator(mappings);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @ResponseBody
  @interface Ajax {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @RestController
  @interface PublicApi {}

  @Controller
  static class BodyMethod {
    @GetMapping("/bad")
    @ResponseBody
    InertiaResponse bad() {
      return null;
    }
  }

  @Controller
  static class ComposedMethod {
    @GetMapping("/bad")
    @Ajax
    InertiaResponse bad() {
      return null;
    }
  }

  @RestController
  static class Rest {
    @GetMapping("/bad")
    InertiaResponse bad() {
      return null;
    }
  }

  @PublicApi
  static class ComposedType {
    @GetMapping("/bad")
    InertiaResponse bad() {
      return null;
    }
  }

  interface Contract {
    @GetMapping("/bad")
    @ResponseBody
    InertiaResponse bad();
  }

  @Controller
  static class InterfaceMethod implements Contract {
    public InertiaResponse bad() {
      return null;
    }
  }

  @Controller
  static class Async {
    @GetMapping("/bad")
    Callable<InertiaResponse> bad() {
      return null;
    }
  }

  @Controller
  static class Wrapped {
    @GetMapping("/bad")
    ResponseEntity<InertiaResponse> bad() {
      return null;
    }
  }

  @Controller
  static class WrongParameter {
    @GetMapping("/bad")
    String bad(InertiaContext context) {
      return "view";
    }
  }

  @Controller
  static class Valid {
    @GetMapping("/page")
    InertiaResponse page(InertiaContext context) {
      return null;
    }

    @GetMapping("/redirect")
    HttpOutcome redirect() {
      return null;
    }

    @GetMapping("/ordinary")
    @ResponseBody
    Map<String, String> ordinary() {
      return Map.of();
    }
  }

  AnnotationConfigWebApplicationContext context(Class<?> controller) {
    var context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(Mvc.class, controller);
    return context;
  }

  @TestFactory
  Stream<DynamicTest> rejectsIncompatibleMappingsAtStartup() {
    return Stream.of(
            BodyMethod.class,
            ComposedMethod.class,
            Rest.class,
            ComposedType.class,
            InterfaceMethod.class,
            Async.class,
            Wrapped.class,
            WrongParameter.class)
        .map(
            controller ->
                DynamicTest.dynamicTest(
                    controller.getSimpleName(),
                    () -> {
                      try (var context = context(controller)) {
                        var failure = assertThrows(IllegalStateException.class, context::refresh);
                        assertTrue(failure.getMessage().contains(controller.getName() + "#bad"));
                      }
                    }));
  }

  @Test
  void typedSynchronousAndOrdinaryRestMappingsCoexist() {
    try (var context = context(Valid.class)) {
      assertDoesNotThrow(context::refresh);
      assertEquals(
          3, context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().size());
    }
  }
}
