package io.inertia.spring;

import io.inertia.core.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Inspects registered MVC mappings after initialization, including composed annotations. */
public final class InertiaHandlerValidator implements SmartInitializingSingleton {
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;

  public InertiaHandlerValidator(ObjectProvider<RequestMappingHandlerMapping> mappings) {
    this.mappings = mappings;
  }

  @Override
  public void afterSingletonsInstantiated() {
    mappings
        .orderedStream()
        .forEach(
            mapping ->
                mapping.getHandlerMethods().values().forEach(InertiaHandlerValidator::validate));
  }

  static boolean supports(HandlerMethod method) {
    Class<?> type = method.getReturnType().getParameterType();
    return type == InertiaResponse.class || type == HttpOutcome.class;
  }

  static void validate(HandlerMethod method) {
    if (supports(method)) {
      if (method.hasMethodAnnotation(ResponseBody.class)
          || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), ResponseBody.class))
        throw invalid(
            method,
            "Use @Controller without @ResponseBody or @RestController for typed Inertia endpoints");
      return;
    }
    if (containsInertia(ResolvableType.forMethodParameter(method.getReturnType()), 0))
      throw invalid(
          method,
          "Typed Inertia return values must be synchronous and unwrapped; async and container return types are unsupported");
    for (var parameter : method.getMethodParameters())
      if (parameter.getParameterType() == InertiaContext.class
          || parameter.getParameterType() == InertiaRequest.class)
        throw invalid(
            method,
            "InertiaContext/InertiaRequest parameters require an InertiaResponse or HttpOutcome return type");
  }

  private static boolean containsInertia(ResolvableType type, int depth) {
    Class<?> resolved = type.resolve();
    if (resolved == InertiaResponse.class || resolved == HttpOutcome.class) return true;
    if (depth >= 8) return false;
    for (var generic : type.getGenerics()) if (containsInertia(generic, depth + 1)) return true;
    return false;
  }

  private static IllegalStateException invalid(HandlerMethod method, String message) {
    return new IllegalStateException(
        message + ": " + method.getBeanType().getName() + "#" + method.getMethod().getName());
  }
}
