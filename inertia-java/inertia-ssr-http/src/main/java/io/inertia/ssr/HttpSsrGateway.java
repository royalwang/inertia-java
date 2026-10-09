package io.inertia.ssr;

import io.inertia.core.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Pooled renderer client. No credential forwarding, redirects, or render retries. */
public final class HttpSsrGateway implements SsrGateway {
  private final HttpClient client;
  private final java.util.function.Function<InertiaRequest, URI> endpoints;
  private final Duration timeout;
  private final int maxBytes;
  private final long timeoutNanos;
  private final PageCodec codec;
  private final Semaphore permits;
  private final boolean verifyBuild;
  private final String rootId;
  private final InertiaObserver observer;
  private final String endpointId;

  public HttpSsrGateway(
      URI endpoint,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec) {
    this(endpoint, connectTimeout, timeout, maxBytes, concurrency, codec, false);
  }

  public HttpSsrGateway(
      URI endpoint,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild) {
    this(
        r -> SsrEndpointResolver.validate(endpoint),
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        null);
    SsrEndpointResolver.validate(endpoint);
  }

  public HttpSsrGateway(
      SsrEndpointResolver endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec) {
    this(endpoints, connectTimeout, timeout, maxBytes, concurrency, codec, false);
  }

  public HttpSsrGateway(
      SsrEndpointResolver endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild) {
    this(
        endpoints::resolve,
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        null);
  }

  public HttpSsrGateway(
      SsrEndpointResolver endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild,
      String rootId) {
    this(
        endpoints::resolve,
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        rootId);
  }

  public HttpSsrGateway(
      URI endpoint,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild,
      String rootId) {
    this(
        r -> SsrEndpointResolver.validate(endpoint),
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        rootId);
    SsrEndpointResolver.validate(endpoint);
  }

  private HttpSsrGateway(
      java.util.function.Function<InertiaRequest, URI> endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild,
      String rootId) {
    this(
        endpoints,
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        rootId,
        InertiaObserver.NOOP,
        "renderer");
  }

  public HttpSsrGateway(
      SsrEndpointResolver endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild,
      String rootId,
      InertiaObserver observer,
      String endpointId) {
    this(
        endpoints::resolve,
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        rootId,
        observer,
        endpointId);
  }

  public HttpSsrGateway(
      URI endpoint,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild,
      String rootId,
      InertiaObserver observer,
      String endpointId) {
    this(
        r -> SsrEndpointResolver.validate(endpoint),
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec,
        verifyBuild,
        rootId,
        observer,
        endpointId);
    SsrEndpointResolver.validate(endpoint);
  }

  private HttpSsrGateway(
      java.util.function.Function<InertiaRequest, URI> endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec,
      boolean verifyBuild,
      String rootId,
      InertiaObserver observer,
      String endpointId) {
    this.observer = Objects.requireNonNull(observer);
    this.endpointId = Observations.endpointId(endpointId);
    if (maxBytes < 1
        || concurrency < 1
        || timeout.isNegative()
        || timeout.isZero()
        || connectTimeout.isNegative()
        || connectTimeout.isZero()) throw new IllegalArgumentException("Invalid SSR limits");
    this.endpoints = endpoints;
    this.timeout = timeout;
    this.timeoutNanos = timeout.toNanos();
    this.maxBytes = maxBytes;
    this.codec = codec;
    this.verifyBuild = verifyBuild;
    this.rootId = rootId == null ? null : InertiaConfig.requireRootId(rootId);
    this.permits = new Semaphore(concurrency);
    client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public CompletionStage<Result> render(Page page, InertiaRequest request) {
    var span =
        Observations.start(
            observer, InertiaObserver.Operation.SSR_HTTP, request, page.component(), endpointId);
    URI endpoint;
    try {
      endpoint = endpoints.apply(request);
    } catch (RuntimeException | Error failure) {
      span.failure(failure);
      throw failure;
    }
    if (endpoint == null) {
      span.fallback("excluded-or-unavailable");
      return CompletableFuture.completedFuture(new Fallback("excluded-or-unavailable"));
    }
    if (!permits.tryAcquire()) {
      span.fallback("overloaded");
      return CompletableFuture.completedFuture(new Fallback("overloaded"));
    }
    CompletableFuture<HttpResponse<byte[]>> transport;
    try {
      HttpRequest outgoing =
          HttpRequest.newBuilder(endpoint)
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(codec.json(page)))
              .build();
      transport = client.sendAsync(outgoing, info -> new LimitedBody(maxBytes));
    } catch (RuntimeException | Error error) {
      permits.release();
      span.failure(error);
      throw error;
    }
    // A separate deadline future preserves the transport's cancelability after timeout.
    var bounded = new CompletableFuture<HttpResponse<byte[]>>();
    var result = new CompletableFuture<Result>();
    var reason = new AtomicReference<>(InertiaObserver.Reason.NONE);
    var status = new AtomicInteger();
    result.whenComplete(
        (value, error) -> {
          // Release transport ownership before invoking application diagnostics.
          if (result.isCancelled()) bounded.cancel(false);
          if (error != null) span.failure(error);
          else
            span.end(
                value instanceof Fallback
                    ? InertiaObserver.Outcome.FALLBACK
                    : InertiaObserver.Outcome.SUCCESS,
                reason.get(),
                status.get(),
                InertiaObserver.ResponseKind.NONE);
        });
    transport.whenComplete(
        (response, error) -> {
          if (error == null) bounded.complete(response);
          else bounded.completeExceptionally(error);
        });
    bounded.orTimeout(timeoutNanos, TimeUnit.NANOSECONDS);
    bounded.whenComplete(
        (response, error) -> {
          if (error != null) transport.cancel(true);
          permits.release();
          if (error != null) {
            reason.set(transportReason(error));
            result.complete(new Fallback("transport-or-timeout"));
          } else {
            try {
              var value = decode(response, page);
              status.set(response.statusCode());
              if (value instanceof Fallback fallback)
                reason.set(Observations.fallbackReason(fallback.reason()));
              result.complete(value);
            } catch (RuntimeException | Error failure) {
              result.completeExceptionally(failure);
            }
          }
        });
    return result;
  }

  private static InertiaObserver.Reason transportReason(Throwable error) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (var current = error; current != null && seen.add(current); current = current.getCause()) {
      if (current instanceof ResponseLimitException) return InertiaObserver.Reason.RESPONSE_LIMIT;
      if (current instanceof TimeoutException || current instanceof HttpTimeoutException)
        return InertiaObserver.Reason.TIMEOUT;
      if (current instanceof java.net.ConnectException
          || current instanceof java.net.UnknownHostException
          || current instanceof java.net.NoRouteToHostException
          || current instanceof java.nio.channels.UnresolvedAddressException)
        return InertiaObserver.Reason.CONNECTION;
      if (current instanceof CancellationException) return InertiaObserver.Reason.CANCELLED;
    }
    return InertiaObserver.Reason.TRANSPORT;
  }

  private static final class ResponseLimitException extends IllegalArgumentException {
    ResponseLimitException() {
      super("SSR response exceeds limit");
    }
  }

  private Result decode(HttpResponse<byte[]> response, Page page) {
    if (response.statusCode() < 200 || response.statusCode() >= 300)
      return new Fallback("http-status");
    try {
      var json = codec.read(new String(response.body(), StandardCharsets.UTF_8));
      if (json == null || json.isNull()) return new Fallback("warming-up");
      if (verifyBuild
          && (!json.path("buildId").isTextual()
              || !json.path("buildId").textValue().equals(page.data().path("version").textValue())))
        return new Fallback("build-mismatch");
      if (rootId != null
          && (!json.path("rootId").isTextual() || !rootId.equals(json.path("rootId").textValue())))
        return new Fallback("root-mismatch");
      if (!json.isObject()
          || !json.path("head").isArray()
          || !json.path("body").isTextual()
          || json.path("body").asText().isBlank()) return new Fallback("invalid-response");
      var head = new ArrayList<String>();
      for (var item : json.path("head")) {
        if (!item.isTextual()) return new Fallback("invalid-response");
        head.add(item.asText());
      }
      return new Rendered(String.join("\n", head), json.path("body").asText());
    } catch (IllegalArgumentException error) {
      return new Fallback("invalid-json");
    }
  }

  static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate =
        HttpResponse.BodySubscribers.ofByteArray();
    private final int limit;
    private final AtomicInteger received = new AtomicInteger();
    private Flow.Subscription subscription;

    LimitedBody(int limit) {
      this.limit = limit;
    }

    public CompletionStage<byte[]> getBody() {
      return delegate.getBody();
    }

    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      delegate.onSubscribe(subscription);
    }

    public void onNext(List<ByteBuffer> buffers) {
      long bytes = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
      if (bytes > limit - received.get()) {
        subscription.cancel();
        delegate.onError(new ResponseLimitException());
        return;
      }
      received.addAndGet((int) bytes);
      delegate.onNext(buffers);
    }

    public void onError(Throwable error) {
      delegate.onError(error);
    }

    public void onComplete() {
      delegate.onComplete();
    }
  }
}
