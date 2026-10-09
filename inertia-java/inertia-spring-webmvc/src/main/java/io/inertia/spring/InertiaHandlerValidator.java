package io.inertia.spring;

import io.inertia.core.*;
import java.lang.reflect.Method;
import java.util.*;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Inspects MVC mappings and exception handlers after initialization, including composed
 * annotations.
 *
 * <p>Typed endpoints must return an unwrapped synchronous Page or outcome, and must not use
 * ResponseBody semantics. Nested generic Inertia values are checked up to eight levels. Ordinary
 * handlers may not request Inertia context/request arguments. This is signature validation, not
 * authentication, component-registry validation, or execution of controller methods.
 */
public final class InertiaHandlerValidator
    implements SmartInitializingSingleton, ApplicationContextAware {
  private ApplicationContext applicationContext;
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;

  /**
   * Creates a validator that resolves MVC mappings after singleton initialization.
   *
   * @param mappings provider of ordered request mappings; retained until initialization validation
   */
  public InertiaHandlerValidator(ObjectProvider<RequestMappingHandlerMapping> mappings) {
    this.mappings = mappings;
  }

  /**
   * Captures the application context for discovering ControllerAdvice types and ancestors.
   *
   * @param applicationContext Spring application context
   */
  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Validates mapped controllers and discovered application exception handlers at startup.
   *
   * @throws IllegalStateException if a handler combines unsupported annotations, wrapped Inertia
   *     values, or Inertia arguments without an explicitly typed return
   */
  @Override
  public void afterSingletonsInstantiated() {
    Set<Class<?>> controllers = new HashSet<>();
    mappings
        .orderedStream()
        .forEach(
            mapping ->
                mapping
                    .getHandlerMethods()
                    .values()
                    .forEach(
                        handler -> {
                          validate(handler);
                          controllers.add(handler.getBeanType());
                        }));
    if (applicationContext != null)
      for (String name :
          BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(
              applicationContext, ControllerAdvice.class)) {
        Class<?> type = applicationContext.getType(name);
        if (type != null) controllers.add(ClassUtils.getUserClass(type));
      }
    controllers.forEach(InertiaHandlerValidator::validateExceptionHandlers);
  }

  static boolean supports(HandlerMethod method) {
    Class<?> type = method.getReturnType().getParameterType();
    return type == InertiaResponse.class || type == HttpOutcome.class;
  }

  static void validate(HandlerMethod method) {
    validate(
        method.getBeanType(),
        method.getMethod(),
        method.getReturnType(),
        method.getMethodParameters(),
        method.hasMethodAnnotation(ResponseBody.class));
  }

  private static void validateExceptionHandlers(Class<?> beanType) {
    MethodIntrospector.selectMethods(
            beanType,
            (MethodIntrospector.MetadataLookup<ExceptionHandler>)
                method ->
                    AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class))
        .keySet()
        .forEach(
            method -> {
              MethodParameter[] parameters = new MethodParameter[method.getParameterCount()];
              for (int i = 0; i < parameters.length; i++)
                parameters[i] = new MethodParameter(method, i).withContainingClass(beanType);
              validate(
                  beanType,
                  method,
                  new MethodParameter(method, -1).withContainingClass(beanType),
                  parameters,
                  AnnotatedElementUtils.hasAnnotation(method, ResponseBody.class));
            });
  }

  private static void validate(
      Class<?> beanType,
      Method method,
      MethodParameter returnType,
      MethodParameter[] parameters,
      boolean responseBody) {
    Class<?> type = returnType.getParameterType();
    if (type == InertiaResponse.class || type == HttpOutcome.class) {
      if (responseBody || AnnotatedElementUtils.hasAnnotation(beanType, ResponseBody.class))
        throw invalid(
            beanType,
            method,
            "Use @Controller/@ControllerAdvice without @ResponseBody, @RestController or @RestControllerAdvice for typed Inertia endpoints");
      return;
    }
    if (containsInertia(ResolvableType.forMethodParameter(returnType), 0))
      throw invalid(
          beanType,
          method,
          "Typed Inertia return values must be synchronous and unwrapped; async and container return types are unsupported");
    for (var parameter : parameters)
      if (parameter.getParameterType() == InertiaContext.class
          || parameter.getParameterType() == InertiaRequest.class)
        throw invalid(
            beanType,
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

  private static IllegalStateException invalid(Class<?> beanType, Method method, String message) {
    return new IllegalStateException(message + ": " + beanType.getName() + "#" + method.getName());
  }
}
