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
 */
public final class InertiaHandlerValidator
    implements SmartInitializingSingleton, ApplicationContextAware {
  private ApplicationContext applicationContext;
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;

  public InertiaHandlerValidator(ObjectProvider<RequestMappingHandlerMapping> mappings) {
    this.mappings = mappings;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

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
