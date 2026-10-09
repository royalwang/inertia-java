package io.inertia.core;

import java.util.*;
import java.util.concurrent.*;

public final class ResponseRenderer {
  private final InertiaConfig config;
  private final PageCodec codec;
  private final PropsResolver resolver;
  private final InertiaObserver observer;
  private final String endpointId;

  public ResponseRenderer(InertiaConfig config, PageCodec codec, PropsResolver resolver) {
    this(config, codec, resolver, InertiaObserver.NOOP, "renderer");
  }

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

  public InertiaObserver observer() {
    return observer;
  }

  public CompletionStage<HttpOutcome> render(InertiaRequest request, InertiaResponse response) {
    return render(new InertiaContext(request, null, codec), response);
  }

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
