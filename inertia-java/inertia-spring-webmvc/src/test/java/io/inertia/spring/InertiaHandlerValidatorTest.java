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

  @RestControllerAdvice
  static class RestAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    InertiaResponse bad() {
      return null;
    }
  }

  @ControllerAdvice
  static class BodyAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    @Ajax
    InertiaResponse bad() {
      return null;
    }
  }

  @ControllerAdvice
  static class AsyncAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    java.util.concurrent.CompletionStage<InertiaResponse> bad() {
      return null;
    }
  }

  @ControllerAdvice
  static class WrappedAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<InertiaResponse> bad() {
      return null;
    }
  }

  @ControllerAdvice
  static class WrongAdviceParameter {
    @ExceptionHandler(IllegalArgumentException.class)
    Map<String, String> bad(InertiaContext context) {
      return Map.of();
    }
  }

  @Controller
  static class LocalAdvice {
    @GetMapping("/local")
    String page() {
      return "view";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    HttpOutcome bad() {
      return null;
    }
  }

  static class GenericAdvice<T> {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<T> bad() {
      return null;
    }
  }

  @ControllerAdvice
  static class InheritedAdvice extends GenericAdvice<InertiaResponse> {}

  @ControllerAdvice
  @org.springframework.web.context.annotation.RequestScope
  static class ScopedBadAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<InertiaResponse> bad() {
      return null;
    }
  }

  @ControllerAdvice
  @org.springframework.web.context.annotation.RequestScope
  static class ValidScopedAdvice {
    ValidScopedAdvice() {
      throw new AssertionError("Validator must not instantiate scoped advice");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    InertiaResponse page(InertiaContext context) {
      return null;
    }
  }

  @RestControllerAdvice
  static class ValidRestAdvice {
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> error() {
      return ResponseEntity.badRequest().body(Map.of());
    }
  }

  @TestFactory
  Stream<DynamicTest> rejectsIncompatibleExceptionHandlersAtStartup() {
    return Stream.of(
            RestAdvice.class,
            BodyAdvice.class,
            AsyncAdvice.class,
            WrappedAdvice.class,
            WrongAdviceParameter.class,
            LocalAdvice.class,
            InheritedAdvice.class,
            ScopedBadAdvice.class)
        .map(
            advice ->
                DynamicTest.dynamicTest(
                    advice.getSimpleName(),
                    () -> {
                      try (var context = context(Valid.class)) {
                        context.register(advice);
                        var failure = assertThrows(IllegalStateException.class, context::refresh);
                        assertTrue(
                            failure.getMessage().contains(advice.getName() + "#bad"),
                            failure.getMessage());
                      }
                    }));
  }

  @Test
  void typedScopedAdviceAndOrdinaryRestAdviceCoexistWithoutInstantiation() {
    try (var context = context(Valid.class)) {
      context.register(ValidScopedAdvice.class, ValidRestAdvice.class);
      assertDoesNotThrow(context::refresh);
    }
  }

  @Test
  void rejectsAdviceInParentContext() {
    try (var parent = new AnnotationConfigApplicationContext(RestAdvice.class);
        var child = context(Valid.class)) {
      child.setParent(parent);
      var failure = assertThrows(IllegalStateException.class, child::refresh);
      assertTrue(failure.getMessage().contains(RestAdvice.class.getName() + "#bad"));
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
