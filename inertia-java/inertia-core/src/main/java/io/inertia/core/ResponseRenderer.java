package io.inertia.core;

import java.util.*;
import java.util.concurrent.*;

public final class ResponseRenderer {
  private final InertiaConfig config;
  private final PageCodec codec;
  private final PropsResolver resolver;

  public ResponseRenderer(InertiaConfig config, PageCodec codec, PropsResolver resolver) {
    this.config = config;
    this.codec = codec;
    this.resolver = resolver;
  }

  public CompletionStage<HttpOutcome> render(InertiaRequest request, InertiaResponse response) {
    return render(new InertiaContext(request, null, codec), response);
  }

  public CompletionStage<HttpOutcome> render(InertiaContext context, InertiaResponse response) {
    InertiaRequest request = context.request();
    response.claim();
    if (!config.components().contains(response.component()))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Unregistered page component"));
    try {
      var stored = context.begin();
      var initial = context.pending();
      var errors = codec.object();
      if (stored.path(InertiaContext.ERRORS).isObject())
        errors.setAll(
            (com.fasterxml.jackson.databind.node.ObjectNode) stored.path(InertiaContext.ERRORS));
      if (initial.path(InertiaContext.ERRORS).isObject())
        errors.setAll(
            (com.fasterxml.jackson.databind.node.ObjectNode) initial.path(InertiaContext.ERRORS));
      String bag = request.header("x-inertia-error-bag");
      com.fasterxml.jackson.databind.JsonNode defaultErrors = errors.path("default");
      var deliveredErrors = codec.object();
      if (bag != null && !bag.isBlank() && defaultErrors.isObject())
        deliveredErrors.set(bag, defaultErrors);
      else if (defaultErrors.isObject())
        deliveredErrors.setAll((com.fasterxml.jackson.databind.node.ObjectNode) defaultErrors);
      errors
          .fields()
          .forEachRemaining(
              e -> {
                if (!e.getKey().equals("default")) deliveredErrors.set(e.getKey(), e.getValue());
              });
      Props shared =
          Props.overlay(
              Props.builder().put("errors", Prop.always(deliveredErrors)).build(),
              config.shared().apply(request),
              context.shared());
      return resolver
          .resolve(request, response.component(), shared, response.props())
          .thenCompose(
              resolved -> {
                boolean preserveBigIntegers =
                    response.preserveBigIntegers() == null
                        ? config.preserveBigIntegers()
                        : response.preserveBigIntegers();
                boolean encryptHistory =
                    response.encryptHistory() == null
                        ? config.encryptHistory()
                        : response.encryptHistory();
                var json = codec.object();
                json.put("component", response.component());
                json.set(
                    "props",
                    preserveBigIntegers ? codec.bigIntegers(resolved.props()) : resolved.props());
                json.put("url", request.url());
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
                CompletionStage<SsrGateway.Result> rendering =
                    config.gateway() != null && response.ssr()
                        ? config.gateway().render(page, request)
                        : CompletableFuture.completedFuture(new SsrGateway.Fallback("disabled"));
                return rendering.thenApply(
                    result -> {
                      String head = "";
                      String body;
                      boolean ssr = result instanceof SsrGateway.Rendered;
                      if (result instanceof SsrGateway.Rendered rendered) {
                        head = rendered.head();
                        body = rendered.body();
                      } else
                        body =
                            "<script data-page=\""
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
                                  new RootView.View(page, head, body, ssr, response.viewData())),
                          "text/html; charset=utf-8");
                    });
              })
          .thenApply(outcome -> ProtocolPolicy.after(request, outcome))
          .whenComplete(
              (outcome, error) -> {
                if (error != null) context.failed();
                else context.complete();
              });
    } catch (Throwable error) {
      context.failed();
      return CompletableFuture.failedFuture(error);
    }
  }

  private static HttpOutcome outcome(InertiaResponse response, String body, String type) {
    return new HttpOutcome(response.status(), response.headers(), body)
        .withHeader("Content-Type", type)
        .vary();
  }
}
