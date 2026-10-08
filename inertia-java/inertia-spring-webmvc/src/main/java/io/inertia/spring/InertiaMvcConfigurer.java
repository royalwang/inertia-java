package io.inertia.spring;

import io.inertia.core.*;
import jakarta.servlet.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** Only explicitly typed Inertia handlers participate in protocol processing. */
public final class InertiaMvcConfigurer implements WebMvcConfigurer {
  public static final String CSP_NONCE_ATTRIBUTE = InertiaMvcConfigurer.class.getName() + ".nonce";
  private static final String CONTEXT = InertiaMvcConfigurer.class.getName() + ".context";
  private final PageCodec codec = new PageCodec();
  private static final String REQUEST = InertiaMvcConfigurer.class.getName() + ".request";
  private final InertiaConfig config;
  private final ResponseRenderer renderer;
  private final Duration deadline;
  private final InertiaErrorPage errorPage;
  private final String sessionNamespace;

  public InertiaMvcConfigurer(InertiaConfig config, ResponseRenderer renderer, Duration deadline) {
    this(config, renderer, deadline, null);
  }

  public InertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      Duration deadline,
      InertiaErrorPage errorPage) {
    this(config, renderer, deadline, errorPage, SessionStore.DEFAULT_NAMESPACE);
  }

  public InertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      Duration deadline,
      InertiaErrorPage errorPage,
      String sessionNamespace) {
    this.config = config;
    this.renderer = renderer;
    this.deadline = deadline;
    this.errorPage = errorPage;
    this.sessionNamespace = SessionStore.requireNamespace(sessionNamespace);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler)
              throws Exception {
            if (!(handler instanceof HandlerMethod method) || !supports(method.getReturnType()))
              return true;
            InertiaHandlerValidator.validate(method);
            var snapshot = snapshot(request);
            var early = ProtocolPolicy.before(snapshot, config.version().get());
            if (early.isPresent()) {
              write(response, early.get());
              return false;
            }
            if (request.getAttribute(CONTEXT) == null)
              request.setAttribute(
                  CONTEXT,
                  new InertiaContext(
                      snapshot,
                      new HttpSessionStore(request.getSession(), sessionNamespace),
                      codec));
            return true;
          }

          @Override
          public void afterCompletion(
              HttpServletRequest request,
              HttpServletResponse response,
              Object handler,
              Exception error) {
            if (error != null) abort(request);
          }
        });
  }

  static void abort(HttpServletRequest request) {
    if (request.getAttribute(CONTEXT) instanceof InertiaContext context) context.abort();
  }

  static InertiaRequest snapshot(HttpServletRequest request) {
    if (request.getAttribute(REQUEST) instanceof InertiaRequest snapshot) return snapshot;
    var headers = new LinkedHashMap<String, String>();
    Collections.list(request.getHeaderNames())
        .forEach(name -> headers.put(name, request.getHeader(name)));
    String url =
        request.getRequestURL()
            + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    Object nonce = request.getAttribute(CSP_NONCE_ATTRIBUTE);
    if (nonce != null && !(nonce instanceof String))
      throw new IllegalArgumentException("Invalid server CSP nonce attribute");
    var snapshot =
        new InertiaRequest(request.getMethod(), URI.create(url), headers, (String) nonce);
    request.setAttribute(REQUEST, snapshot);
    return snapshot;
  }

  @Override
  public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    int position = 0;
    for (int i = 0; i < resolvers.size(); i++)
      if (resolvers.get(i)
          instanceof
          org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver)
        position = i + 1;
    resolvers.add(position, new InertiaExceptionResolver(renderer, deadline, errorPage));
  }

  private static boolean supports(MethodParameter parameter) {
    return InertiaResponse.class.equals(parameter.getParameterType())
        || HttpOutcome.class.equals(parameter.getParameterType());
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(
        new HandlerMethodArgumentResolver() {
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == InertiaRequest.class
                || parameter.getParameterType() == InertiaContext.class;
          }

          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer container,
              NativeWebRequest request,
              org.springframework.web.bind.support.WebDataBinderFactory factory) {
            var value =
                request
                    .getNativeRequest(HttpServletRequest.class)
                    .getAttribute(
                        parameter.getParameterType() == InertiaContext.class ? CONTEXT : REQUEST);
            if (value == null)
              throw new IllegalStateException(
                  "InertiaRequest requires an Inertia response handler");
            return value;
          }
        });
  }

  @Override
  public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
    handlers.add(
        new HandlerMethodReturnValueHandler() {
          public boolean supportsReturnType(MethodParameter parameter) {
            return supports(parameter);
          }

          public void handleReturnValue(
              Object value,
              MethodParameter parameter,
              ModelAndViewContainer container,
              NativeWebRequest webRequest)
              throws Exception {
            var request = webRequest.getNativeRequest(HttpServletRequest.class);
            var snapshot = (InertiaRequest) request.getAttribute(REQUEST);
            if (snapshot == null)
              throw new IllegalStateException("Missing Inertia request snapshot");
            HttpOutcome outcome;
            if (value instanceof InertiaResponse response) {
              var context = (InertiaContext) request.getAttribute(CONTEXT);
              var pending = renderer.render(context, response).toCompletableFuture();
              try {
                outcome = pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
              } catch (TimeoutException | InterruptedException error) {
                try {
                  context.abort();
                } catch (RuntimeException cleanup) {
                  error.addSuppressed(cleanup);
                }
                pending.cancel(true);
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw error;
              }
            } else if (value instanceof HttpOutcome response) {
              outcome = ProtocolPolicy.after(snapshot, response);
              ((InertiaContext) request.getAttribute(CONTEXT)).commitRedirect();
            } else throw new IllegalStateException("Inertia handler returned null");
            write(webRequest.getNativeResponse(HttpServletResponse.class), outcome);
            container.setRequestHandled(true);
          }
        });
  }

  static void write(HttpServletResponse response, HttpOutcome outcome) throws Exception {
    response.setStatus(outcome.status());
    response.setCharacterEncoding("UTF-8");
    outcome
        .headers()
        .forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
    response.getWriter().write(outcome.body());
  }
}
