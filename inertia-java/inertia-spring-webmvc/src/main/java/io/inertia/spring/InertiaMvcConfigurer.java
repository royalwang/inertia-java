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
  private static final String ADVICE_CONTEXT =
      InertiaMvcConfigurer.class.getName() + ".advice-context";
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
            long versionStarted = System.nanoTime();
            var early = ProtocolPolicy.before(snapshot, config.version().get());
            if (early.isPresent()) {
              Observations.publish(
                  renderer.observer(),
                  new InertiaObserver.Event(
                      InertiaObserver.Operation.VERSION_CONFLICT,
                      InertiaObserver.Outcome.CONFLICT,
                      InertiaObserver.Reason.VERSION_MISMATCH,
                      System.nanoTime() - versionStarted,
                      early.get().status(),
                      Observations.responseKind(early.get()),
                      snapshot.requestId(),
                      "",
                      "none"));
              writeObserved(request, response, early.get(), renderer.observer(), "");
              return false;
            }
            if (request.getAttribute(CONTEXT) == null)
              request.setAttribute(
                  CONTEXT,
                  new InertiaContext(
                      snapshot,
                      new HttpSessionStore(request.getSession(), sessionNamespace),
                      codec,
                      renderer.observer()));
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

  /** Typed application error pages get a fresh sessionless context, like the library error page. */
  private void prepareAdviceContext(MethodParameter parameter, HttpServletRequest request) {
    var method = parameter.getMethod();
    if (method == null
        || method.getReturnType() != InertiaResponse.class
        || !org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
            method, org.springframework.web.bind.annotation.ExceptionHandler.class)
        || request.getAttribute(REQUEST) == null
        || request.getAttribute(ADVICE_CONTEXT) != null) return;
    abort(request);
    request.setAttribute(CONTEXT, new InertiaContext(snapshot(request), null, codec));
    request.setAttribute(ADVICE_CONTEXT, Boolean.TRUE);
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
            prepareAdviceContext(parameter, request.getNativeRequest(HttpServletRequest.class));
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
            prepareAdviceContext(parameter, request);
            var snapshot = (InertiaRequest) request.getAttribute(REQUEST);
            if (snapshot == null)
              throw new IllegalStateException("Missing Inertia request snapshot");
            HttpOutcome outcome;
            if (value instanceof InertiaResponse response) {
              var context = (InertiaContext) request.getAttribute(CONTEXT);
              var pending = renderer.render(context, response).toCompletableFuture();
              try {
                outcome = pending.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
              } catch (TimeoutException | InterruptedException error) {
                try {
                  context.abort();
                } catch (RuntimeException | Error cleanup) {
                  error.addSuppressed(cleanup);
                } finally {
                  pending.cancel(true);
                }
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw error;
              }
            } else if (value instanceof HttpOutcome response) {
              outcome = ProtocolPolicy.after(snapshot, response);
              ((InertiaContext) request.getAttribute(CONTEXT)).commitRedirect();
            } else throw new IllegalStateException("Inertia handler returned null");
            writeObserved(
                request,
                webRequest.getNativeResponse(HttpServletResponse.class),
                outcome,
                renderer.observer(),
                value instanceof InertiaResponse page ? page.component() : "");
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

  /** Reports an adapter write attempt, not browser receipt or successful business status. */
  static void writeObserved(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpOutcome outcome,
      InertiaObserver observer,
      String component)
      throws Exception {
    Observations.Span span = null;
    try {
      span =
          Observations.start(
              observer, InertiaObserver.Operation.RESPONSE, snapshot(request), component, "none");
    } catch (RuntimeException ignored) {
      // A rejected request snapshot must not prevent the safe plaintext error response.
    }
    try {
      write(response, outcome);
      if (span != null) span.success(outcome);
    } catch (Exception | Error failure) {
      if (span != null) span.failure(failure);
      throw failure;
    }
  }
}
