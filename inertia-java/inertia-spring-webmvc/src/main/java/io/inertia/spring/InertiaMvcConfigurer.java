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

/**
 * Servlet MVC integration for synchronous, unwrapped {@link InertiaResponse} and {@link
 * HttpOutcome} controller return types.
 *
 * <p>The interceptor captures request metadata, handles version conflicts before controller work,
 * and creates namespaced session state. Return handlers wait for asynchronous provider/SSR work
 * within the response budget on the Servlet thread; this is not Servlet asynchronous dispatch.
 * Application exception advice keeps precedence over the final error-page resolver.
 *
 * <p>Use {@code @Controller} without ResponseBody, RestController, or async/container wrappers.
 * Proxy URL reconstruction and server CSP nonce generation belong to application configuration.
 * Boot registers the session mutex listener; plain Spring applications must arrange equivalent
 * stable session locking when their container uses session wrappers.
 */
public final class InertiaMvcConfigurer implements WebMvcConfigurer {
  /**
   * Servlet request attribute for a trusted String CSP nonce, captured before rendering.
   *
   * <p>Set this from server middleware; the adapter never reads a nonce from client headers.
   */
  public static final String CSP_NONCE_ATTRIBUTE = InertiaMvcConfigurer.class.getName() + ".nonce";

  private static final String CONTEXT = InertiaMvcConfigurer.class.getName() + ".context";
  private final PageCodec codec;
  private static final String ADVICE_CONTEXT =
      InertiaMvcConfigurer.class.getName() + ".advice-context";
  private static final String SESSION = InertiaMvcConfigurer.class.getName() + ".session";
  private static final String REQUEST = InertiaMvcConfigurer.class.getName() + ".request";
  private final InertiaConfig config;
  private final ResponseRenderer renderer;
  private final Duration deadline;
  private final InertiaErrorPage errorPage;
  private final InertiaSessionStoreFactory sessionStores;

  /**
   * Creates the MVC integration with no custom error Page and the default session namespace.
   *
   * @param config application render policy and current version supplier
   * @param renderer application-scoped renderer
   * @param deadline positive maximum wait for Page rendering on the Servlet request thread
   */
  public InertiaMvcConfigurer(InertiaConfig config, ResponseRenderer renderer, Duration deadline) {
    this(config, renderer, deadline, null);
  }

  /**
   * Creates the integration with an optional final error Page and default session namespace.
   *
   * @param config application render policy and current version supplier
   * @param renderer application-scoped renderer
   * @param deadline positive maximum wait for Page rendering on the Servlet request thread
   * @param errorPage application safe-error policy, or null for plain-text fallback
   */
  public InertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      Duration deadline,
      InertiaErrorPage errorPage) {
    this(config, renderer, deadline, errorPage, SessionStore.DEFAULT_NAMESPACE);
  }

  /**
   * Creates the integration with namespaced session effects and a default Page codec.
   *
   * @param config application render policy and current version supplier
   * @param renderer application-scoped renderer
   * @param deadline positive maximum wait for Page rendering on the Servlet request thread
   * @param errorPage application safe-error policy, or null for plain-text fallback
   * @param sessionNamespace validated session-state namespace
   * @throws IllegalArgumentException if the namespace is invalid
   */
  public InertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      Duration deadline,
      InertiaErrorPage errorPage,
      String sessionNamespace) {
    this(config, renderer, deadline, errorPage, sessionNamespace, new PageCodec());
  }

  /**
   * Creates the integration using the same codec for rendering and request-owned effects.
   *
   * <p>Use this overload when configuring Jackson modules or serializers. This constructor
   * validates codec and namespace, but budget ordering/positivity must be validated by the
   * application or Boot properties. It does not take ownership of renderer dependencies or their
   * shutdown.
   *
   * @param config application render policy and current version supplier
   * @param renderer application-scoped renderer
   * @param deadline positive maximum wait for Page rendering on the Servlet request thread
   * @param errorPage application safe-error policy, or null for plain-text fallback
   * @param sessionNamespace validated session-state namespace
   * @param codec non-null codec shared with the renderer
   * @throws IllegalArgumentException if the namespace is invalid
   * @throws NullPointerException if codec is null
   */
  public InertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      Duration deadline,
      InertiaErrorPage errorPage,
      String sessionNamespace,
      PageCodec codec) {
    this(
        config,
        renderer,
        deadline,
        errorPage,
        sessionNamespace,
        codec,
        request -> new HttpSessionStore(request.getSession(), sessionNamespace));
  }

  /**
   * Creates MVC integration with a request-owned session store factory. Existing constructors
   * retain their single-node HttpSession behavior.
   *
   * @param config application rendering policy
   * @param renderer application-owned renderer
   * @param deadline Servlet response budget
   * @param errorPage optional error Page policy
   * @param sessionNamespace validated application namespace
   * @param codec shared Page codec
   * @param sessionStores non-null trusted request-to-store factory
   */
  public InertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      Duration deadline,
      InertiaErrorPage errorPage,
      String sessionNamespace,
      PageCodec codec,
      InertiaSessionStoreFactory sessionStores) {
    this.codec = Objects.requireNonNull(codec);
    this.config = config;
    this.renderer = renderer;
    this.deadline = deadline;
    this.errorPage = errorPage;
    SessionStore.requireNamespace(sessionNamespace);
    this.sessionStores = Objects.requireNonNull(sessionStores);
  }

  /**
   * Registers protocol pre-processing only for explicitly typed Inertia handlers.
   *
   * <p>A version-conflict response bypasses controller invocation and session creation. Otherwise
   * the adapter captures metadata and attaches request-owned context/session state before argument
   * binding.
   *
   * @param registry MVC interceptor registry
   */
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
            if (request.getAttribute(CONTEXT) == null) {
              var session = Objects.requireNonNull(sessionStores.create(request));
              request.setAttribute(SESSION, session);
              request.setAttribute(
                  CONTEXT, new InertiaContext(snapshot, session, codec, renderer.observer()));
            }
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

  /**
   * Adds one final Inertia error resolver after application ExceptionHandler resolution.
   *
   * <p>The fallback uses a fresh sessionless error context and attempts one error Page before plain
   * text. It does not consume the failed Page's restored one-time delivery or handle ordinary REST.
   *
   * @param resolvers ordered mutable MVC exception-resolver list
   */
  @Override
  public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    int position = 0;
    for (int i = 0; i < resolvers.size(); i++)
      if (resolvers.get(i)
          instanceof
          org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver)
        position = i + 1;
    resolvers.add(position, new InertiaExceptionResolver(renderer, deadline, errorPage, codec));
  }

  /** Error pages are sessionless; error outcomes can commit only their own fresh effects. */
  private void prepareAdviceContext(MethodParameter parameter, HttpServletRequest request) {
    var method = parameter.getMethod();
    if (method == null) return;
    Class<?> returnType =
        org.springframework.core.ResolvableType.forMethodReturnType(
                method, parameter.getContainingClass())
            .resolve();
    if ((returnType != InertiaResponse.class && returnType != HttpOutcome.class)
        || !org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
            method, org.springframework.web.bind.annotation.ExceptionHandler.class)
        || request.getAttribute(REQUEST) == null
        || request.getAttribute(ADVICE_CONTEXT) != null) return;
    abort(request);
    SessionStore session =
        returnType == HttpOutcome.class ? (SessionStore) request.getAttribute(SESSION) : null;
    request.setAttribute(
        CONTEXT, new InertiaContext(snapshot(request), session, codec, renderer.observer()));
    request.setAttribute(ADVICE_CONTEXT, Boolean.TRUE);
  }

  private static boolean supports(MethodParameter parameter) {
    return InertiaResponse.class.equals(parameter.getParameterType())
        || HttpOutcome.class.equals(parameter.getParameterType());
  }

  /**
   * Registers request/context arguments for participating typed handlers and typed advice.
   *
   * <p>Typed error Page advice gets a fresh sessionless context. Typed outcome advice retains the
   * original store/namespace for its own new effects; invalidated or detached state is not rebound.
   *
   * @param resolvers mutable MVC argument-resolver list
   */
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

  /**
   * Registers typed Page/outcome handlers with bounded waiting and pre-write effect completion.
   *
   * <p>Timeout or interruption aborts context delivery and cancels the pending render. A redirect
   * commits its pending effects before writing. Later write failure cannot undo completed delivery.
   *
   * @param handlers mutable MVC return-value-handler list
   */
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
