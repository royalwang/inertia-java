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

/** Pooled renderer client. No credential forwarding, redirects, or render retries. */
public final class HttpSsrGateway implements SsrGateway {
  private final HttpClient client;
  private final java.util.function.Function<InertiaRequest, URI> endpoints;
  private final Duration timeout;
  private final int maxBytes;
  private final PageCodec codec;
  private final Semaphore permits;

  public HttpSsrGateway(
      URI endpoint,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec) {
    this(
        r -> SsrEndpointResolver.validate(endpoint),
        connectTimeout,
        timeout,
        maxBytes,
        concurrency,
        codec);
    SsrEndpointResolver.validate(endpoint);
  }

  public HttpSsrGateway(
      SsrEndpointResolver endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec) {
    this(endpoints::resolve, connectTimeout, timeout, maxBytes, concurrency, codec);
  }

  private HttpSsrGateway(
      java.util.function.Function<InertiaRequest, URI> endpoints,
      Duration connectTimeout,
      Duration timeout,
      int maxBytes,
      int concurrency,
      PageCodec codec) {
    if (maxBytes < 1 || concurrency < 1 || timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Invalid SSR limits");
    this.endpoints = endpoints;
    this.timeout = timeout;
    this.maxBytes = maxBytes;
    this.codec = codec;
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
    URI endpoint = endpoints.apply(request);
    if (endpoint == null)
      return CompletableFuture.completedFuture(new Fallback("excluded-or-unavailable"));
    if (!permits.tryAcquire()) return CompletableFuture.completedFuture(new Fallback("overloaded"));
    HttpRequest outgoing =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(codec.json(page)))
            .build();
    return client
        .sendAsync(outgoing, info -> new LimitedBody(maxBytes))
        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .<Result>handle(
            (response, error) -> {
              if (error != null) return new Fallback("transport-or-timeout");
              if (response.statusCode() < 200 || response.statusCode() >= 300)
                return new Fallback("http-status");
              try {
                var json = codec.read(new String(response.body(), StandardCharsets.UTF_8));
                if (json == null || json.isNull()) return new Fallback("warming-up");
                if (!json.isObject()
                    || !json.path("head").isArray()
                    || !json.path("body").isTextual()
                    || json.path("body").asText().isBlank())
                  return new Fallback("invalid-response");
                var head = new ArrayList<String>();
                for (var item : json.path("head")) {
                  if (!item.isTextual()) return new Fallback("invalid-response");
                  head.add(item.asText());
                }
                return new Rendered(String.join("\n", head), json.path("body").asText());
              } catch (IllegalArgumentException e) {
                return new Fallback("invalid-json");
              }
            })
        .whenComplete((r, e) -> permits.release());
  }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
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
        delegate.onError(new IllegalArgumentException("SSR response exceeds limit"));
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
