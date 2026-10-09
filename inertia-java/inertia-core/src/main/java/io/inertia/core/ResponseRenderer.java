package io.inertia.core;

import java.util.*;
import java.util.concurrent.*;

/**
 * Application-scoped Page render pipeline, producing an outcome before transport writes.
 *
 * <p>Each render owns prop/SSR cancellation and a one-time session delivery. Successful completion
 * consumes the reserved delivery before the framework writes HTTP bytes. Later write failure cannot
 * roll back consumed flash. The caller owns the executor, gateway, and shell supplied through its
 * dependencies, and must apply {@link ProtocolPolicy#before(InertiaRequest, String)} at its
 * boundary.
 */
public final class ResponseRenderer {
  private final InertiaConfig config;
  private final PageCodec codec;
  private final PropsResolver resolver;
  private final InertiaObserver observer;
  private final String endpointId;

  /**
   * Creates a renderer with no-op observation and diagnostic endpoint label {@code renderer}.
   *
   * @param config application render policy
   * @param codec Page JSON codec
   * @param resolver application-scoped prop resolver
   */
  public ResponseRenderer(InertiaConfig config, PageCodec codec, PropsResolver resolver) {
    this(config, codec, resolver, InertiaObserver.NOOP, "renderer");
  }

  /**
   * Creates a renderer with explicit diagnostics.
   *
   * @param config application render policy
   * @param codec Page JSON codec
   * @param resolver application-scoped prop resolver
   * @param observer non-null operation observer
   * @param endpointId bounded diagnostic label; does not select the SSR URL
   * @throws NullPointerException if observer is null
   * @throws IllegalArgumentException if endpointId is unsafe
   */
  public ResponseRenderer(
      InertiaConfig config,
      PageCodec codec,
      PropsResolver resolver,
      InertiaObserver observer,
      String endpointId) {
    this.observer = Objects.requireNonNull(observer);
    this.endpointId = Observations.endpointId(endpointId);
    this.config = config;
    this.codec = codec;
    this.resolver = resolver;
  }

  /**
   * Returns the configured observer for adapter-level protocol and response events.
   *
   * @return operation observer
   */
  public InertiaObserver observer() {
    return observer;
  }

  /**
   * Renders using a fresh sessionless context.
   *
   * @param request captured request metadata
   * @param response request-owned response definition, claimed once
   * @return stage producing the prepared outcome or failing with the render error
   * @throws IllegalStateException synchronously if the response was already claimed
   */
  public CompletionStage<HttpOutcome> render(InertiaRequest request, InertiaResponse response) {
    return render(new InertiaContext(request, null, codec), response);
  }

  /**
   * Renders one response using request-owned state and reserved one-time session data.
   *
   * <p>JSON visits resolve props without invoking SSR. HTML visits use SSR or the CSR shell;
   * required SSR promotes unavailability to {@link SsrRequiredException}. Failure or cancellation
   * attempts session restoration and cancels owned tasks. The result includes post-protocol
   * normalization; this method does not write to a Servlet response or perform the
   * pre-version-conflict check.
   *
   * @param context unused request-owned context
   * @param response request-owned response definition, claimed once
   * @return cancellable stage for a prepared outcome; provider, shell, session, and SSR errors fail
   *     it
   * @throws IllegalStateException synchronously if response or context was already used
   */
  public CompletionStage<HttpOutcome> render(InertiaContext context, InertiaResponse response) {
    InertiaRequest request = context.request();
    var span =
        Observations.start(
            observer,
            InertiaObserver.Operation.RENDER,
            request,
            config.components().contains(response.component())
                ? response.component()
                : "unregistered",
            "none");
    try {
      response.claim();
    } catch (RuntimeException error) {
      span.failure(error);
      throw error;
    }
    if (!config.components().contains(response.component())) {
      var error = new IllegalArgumentException("Unregistered page component");
      span.failure(error);
      return CompletableFuture.failedFuture(error);
    }
    try {
      context.observer(observer, response.component());
    } catch (RuntimeException error) {
      span.failure(error);
      throw error;
    }
    var scope = new CancellationScope();
    var result = new OperationFuture<HttpOutcome>(scope, context::complete, context::fail);
    result.whenComplete(
        (outcome, error) -> {
          if (error == null) span.success(outcome);
          else span.failure(error);
        });
    try {
      var stored = context.begin();
      String pageUrl = config.pageUrl(request);
      var initial = context.pending();
      var errors =
          ErrorBags.fromJson(stored.get(InertiaContext.ERRORS))
              .merge(ErrorBags.fromJson(initial.get(InertiaContext.ERRORS)));
      var deliveredErrors =
          errors.toProp(request.header("x-inertia-error-bag"), config.allErrors());
      Props shared =
          Props.overlay(
              Props.from(
                  Props.Source.INTERNAL_ERRORS,
                  Props.builder().put("errors", Prop.always(deliveredErrors)).build()),
              Props.from(Props.Source.CONFIG_SHARED, config.shared().apply(request)),
              Props.from(Props.Source.REQUEST_SHARED, context.shared()));
      var resolving =
          scope.track(
              resolver
                  .resolve(
                      request,
                      response.component(),
                      shared,
                      response.props(),
                      config.exposeSharedPropKeys())
                  .toCompletableFuture());
      resolving
          .thenCompose(
              resolved -> {
                if (result.settled())
                  return CompletableFuture.failedFuture(new CancellationException());
                boolean preserveBigIntegers =
                    response.preserveBigIntegers() == null
                        ? config.preserveBigIntegers()
                        : response.preserveBigIntegers();
                boolean encryptHistory =
                    response.encryptHistory() == null
                        ? (context.encryptHistory() == null
                            ? config.encryptHistory()
                            : context.encryptHistory())
                        : response.encryptHistory();
                var json = codec.object();
                json.put("component", response.component());
                json.set(
                    "props",
                    preserveBigIntegers ? codec.bigIntegers(resolved.props()) : resolved.props());
                json.put("url", pageUrl);
                json.put("version", config.version().get());
                json.setAll(resolved.metadata());
                if (preserveBigIntegers) json.put("preserveBigIntegers", true);
                if (encryptHistory) json.put("encryptHistory", true);
                context.prepared();
                var current = context.pending();
                var flash = codec.object();
                if (stored.path(InertiaContext.FLASH).isObject())
                  flash.setAll(
                      (com.fasterxml.jackson.databind.node.ObjectNode)
                          stored.path(InertiaContext.FLASH));
                if (current.path(InertiaContext.FLASH).isObject())
                  flash.setAll(
                      (com.fasterxml.jackson.databind.node.ObjectNode)
                          current.path(InertiaContext.FLASH));
                response.flash().forEach((key, value) -> flash.set(key, codec.value(value)));
                if (!flash.isEmpty())
                  json.set("flash", preserveBigIntegers ? codec.bigIntegers(flash) : flash);
                if (response.clearHistory()
                    || stored.path(InertiaContext.CLEAR).asBoolean()
                    || current.path(InertiaContext.CLEAR).asBoolean())
                  json.put("clearHistory", true);
                if (stored.path(InertiaContext.FRAGMENT).asBoolean()
                    || current.path(InertiaContext.FRAGMENT).asBoolean())
                  json.put("preserveFragment", true);
                Page page = new Page(json);
                if (request.isInertia())
                  return CompletableFuture.completedFuture(
                      outcome(response, codec.json(page), "application/json")
                          .withHeader("X-Inertia", "true"));
                var ssrSpan =
                    Observations.start(
                        observer,
                        InertiaObserver.Operation.SSR,
                        request,
                        response.component(),
                        endpointId);
                CompletionStage<SsrGateway.Result> rendering;
                try {
                  rendering =
                      config.gateway() != null && response.ssr()
                          ? config.gateway().render(page, request)
                          : CompletableFuture.completedFuture(new SsrGateway.Fallback("disabled"));
                  if (rendering == null && response.ssrRequired())
                    throw new SsrRequiredException(InertiaObserver.Reason.INVALID_RESPONSE);
                  Objects.requireNonNull(rendering, "SSR gateway returned no completion stage");
                } catch (Throwable error) {
                  ssrSpan.failure(error);
                  if (response.ssrRequired()
                      && error instanceof RuntimeException
                      && !(error instanceof SsrRequiredException)
                      && Observations.failureReason(error) != InertiaObserver.Reason.CANCELLED)
                    throw new SsrRequiredException(Observations.failureReason(error), error);
                  throw error;
                }
                rendering.whenComplete(
                    (value, error) -> {
                      if (error != null) ssrSpan.failure(error);
                      else if (value instanceof SsrGateway.Fallback fallback)
                        ssrSpan.fallback(fallback.reason());
                      else if (value instanceof SsrGateway.Rendered) ssrSpan.success();
                      else ssrSpan.fallback("invalid-response");
                    });
                scope.track(rendering.toCompletableFuture());
                if (result.settled())
                  return CompletableFuture.failedFuture(new CancellationException());
                if (response.ssrRequired())
                  rendering =
                      rendering.handle(
                          (value, error) -> {
                            if (error != null) {
                              if (Observations.failureReason(error)
                                  == InertiaObserver.Reason.CANCELLED)
                                throw new CompletionException(error);
                              if (error instanceof Error) throw (Error) error;
                              throw new SsrRequiredException(
                                  Observations.failureReason(error), error);
                            }
                            if (value instanceof SsrGateway.Fallback fallback)
                              throw new SsrRequiredException(
                                  Observations.fallbackReason(fallback.reason()));
                            if (!(value instanceof SsrGateway.Rendered))
                              throw new SsrRequiredException(
                                  InertiaObserver.Reason.INVALID_RESPONSE);
                            return value;
                          });
                return rendering.thenApply(
                    renderedResult -> {
                      if (result.settled()) throw new CancellationException();
                      String head = "";
                      String body;
                      boolean ssr = renderedResult instanceof SsrGateway.Rendered;
                      if (renderedResult instanceof SsrGateway.Rendered rendered) {
                        head = rendered.head();
                        body = rendered.body();
                      } else
                        body =
                            "<script"
                                + CspNonce.attribute(request.nonce())
                                + " data-page=\""
                                + config.rootId()
                                + "\" type=\"application/json\">"
                                + codec.htmlJson(page)
                                + "</script><div id=\""
                                + config.rootId()
                                + "\"></div>";
                      return outcome(
                          response,
                          config
                              .rootView()
                              .render(
                                  new RootView.View(
                                      page, head, body, ssr, response.viewData(), request.nonce())),
                          "text/html; charset=utf-8");
                    });
              })
          .thenApply(outcome -> ProtocolPolicy.after(request, outcome))
          .whenComplete(
              (outcome, error) -> {
                if (error != null) result.completeExceptionally(error);
                else result.complete(outcome);
              });
    } catch (Throwable error) {
      result.completeExceptionally(error);
    }
    return result;
  }

  private static HttpOutcome outcome(InertiaResponse response, String body, String type) {
    return new HttpOutcome(response.status(), response.headers(), body)
        .withHeader("Content-Type", type)
        .vary();
  }
}
