package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Plans before execution; each resolve owns metadata and a single total deadline. */
public final class PropsResolver {
  public record Resolved(ObjectNode props, ObjectNode metadata) {}

  private record Value(JsonNode json, ScrollPage scroll) {}

  private record Plan(String path, Prop prop, CompletableFuture<Value> value, boolean excluded) {}

  private final PageCodec codec;
  private final Executor executor;
  private final Duration deadline;
  private final int maxConcurrency;
  private final Clock clock;
  private final InertiaObserver observer;

  public PropsResolver(PageCodec codec, Executor executor, Duration deadline, int maxConcurrency) {
    this(codec, executor, deadline, maxConcurrency, Clock.systemUTC());
  }

  public PropsResolver(
      PageCodec codec, Executor executor, Duration deadline, int maxConcurrency, Clock clock) {
    this(codec, executor, deadline, maxConcurrency, clock, InertiaObserver.NOOP);
  }

  public PropsResolver(
      PageCodec codec,
      Executor executor,
      Duration deadline,
      int maxConcurrency,
      Clock clock,
      InertiaObserver observer) {
    this.observer = Objects.requireNonNull(observer);
    this.codec = Objects.requireNonNull(codec);
    this.executor = Objects.requireNonNull(executor);
    this.clock = Objects.requireNonNull(clock);
    if (deadline.isZero() || deadline.isNegative() || maxConcurrency < 1)
      throw new IllegalArgumentException("Invalid resolution budget");
    this.deadline = deadline;
    this.maxConcurrency = maxConcurrency;
  }

  public CompletionStage<Resolved> resolve(
      InertiaRequest request, String component, Props shared, Props props) {
    var span =
        Observations.start(observer, InertiaObserver.Operation.PROPS, request, component, "none");
    var scope = new CancellationScope();
    var result = new OperationFuture<Resolved>(scope, () -> {}, error -> {});
    var state =
        new State(request, request.isPartial(component), scope, result::completeExceptionally);
    result.whenComplete(
        (value, error) -> {
          if (error == null) span.success();
          else span.failure(error);
        });
    result.orTimeout(deadline.toNanos(), TimeUnit.NANOSECONDS);
    try {
      var all = Props.overlay(shared, Props.from(Props.Source.PAGE, props));
      for (var override : all.overrides())
        Observations.publish(
            observer,
            new InertiaObserver.Event(
                InertiaObserver.Operation.PROP_OVERRIDE,
                InertiaObserver.Outcome.SUCCESS,
                override.path().equals("errors")
                    ? InertiaObserver.Reason.ERRORS_OVERRIDE
                    : InertiaObserver.Reason.PROP_OVERRIDE,
                0,
                0,
                InertiaObserver.ResponseKind.NONE,
                request.requestId(),
                component,
                "none"));
      var sharedKeys =
          codec.value(
              shared.entries().keySet().stream().map(k -> k.split("\\.")[0]).distinct().toList());
      if (!sharedKeys.isEmpty()) state.metadata.set("sharedProps", sharedKeys);
      state
          .level(all, "", false)
          .whenComplete(
              (values, error) -> {
                if (error == null) result.complete(new Resolved(values, state.metadata));
                else result.completeExceptionally(error);
              });
    } catch (Throwable error) {
      result.completeExceptionally(error);
    }
    return result;
  }

  private Value value(Object source) {
    if (source instanceof ScrollPage page) {
      var out = codec.object();
      out.set(page.wrapper(), codec.value(page.data()));
      return new Value(out, page);
    }
    return new Value(codec.value(source), null);
  }

  private final class State {
    final InertiaRequest request;
    final boolean partial;
    final ObjectNode metadata = codec.object();
    final Semaphore permits = new Semaphore(maxConcurrency);
    final CancellationScope scope;
    final java.util.function.Consumer<Throwable> failure;

    State(
        InertiaRequest request,
        boolean partial,
        CancellationScope scope,
        java.util.function.Consumer<Throwable> failure) {
      this.request = request;
      this.partial = partial;
      this.scope = scope;
      this.failure = failure;
    }

    boolean matches(String path) {
      var only = request.list("x-inertia-partial-data");
      return (only.isEmpty() || only.stream().anyMatch(p -> within(path, p) || within(p, path)))
          && !excepted(path);
    }

    boolean excepted(String path) {
      return request.list("x-inertia-partial-except").stream().anyMatch(p -> within(path, p));
    }

    boolean contributes(String path) {
      var only = request.list("x-inertia-partial-data");
      return !partial
          || ((only.isEmpty() || only.stream().anyMatch(p -> within(path, p))) && !excepted(path));
    }

    boolean loaded(Prop prop, String path) {
      return prop.onceOptions() != null
          && !prop.onceOptions().fresh()
          && request
              .list("x-inertia-except-once-props")
              .contains(prop.onceOptions().key() == null ? path : prop.onceOptions().key());
    }

    CompletableFuture<ObjectNode> level(Props props, String prefix, boolean inheritedAlways) {
      if (scope.stopped()) return CompletableFuture.failedFuture(new CancellationException());
      var plans = new ArrayList<Plan>();
      for (var entry : props.entries().entrySet()) {
        if (scope.stopped()) return CompletableFuture.failedFuture(new CancellationException());
        String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
        Prop prop = entry.getValue();
        if (partial && !inheritedAlways && !prop.always() && !matches(path)) continue;
        if (!partial
            && (prop.loading() != Prop.Loading.EAGER
                || (request.isInertia() && loaded(prop, path)))) {
          plans.add(new Plan(path, prop, null, true));
          continue;
        }
        CompletableFuture<Value> future;
        if (prop.source() instanceof Prop.Computed || prop.source() instanceof Prop.Async) {
          future = schedule(path, prop.source());
        } else if (prop.source() instanceof Prop.Literal literal) {
          Value literalValue = value(literal.value());
          future =
              CompletableFuture.completedFuture(
                  new Value(
                      filter(literalValue.json(), path, inheritedAlways || prop.always()),
                      literalValue.scroll()));
        } else future = null;

        if (future != null)
          future.whenComplete(
              (value, error) -> {
                if (error != null && !prop.rescued())
                  failure.accept(new PropResolutionException(path, error));
                else if (error == null && prop.scroll() && value.scroll() == null)
                  failure.accept(
                      new PropResolutionException(
                          path,
                          new IllegalArgumentException("Scroll prop must return ScrollPage")));
              });
        plans.add(new Plan(path, prop, future, false));
      }
      var pending =
          plans.stream()
              .filter(p -> p.value() != null)
              .map(p -> p.value().handle((r, e) -> null))
              .toArray(CompletableFuture[]::new);
      return CompletableFuture.allOf(pending)
          .thenCompose(
              ignored -> {
                if (scope.stopped())
                  return CompletableFuture.failedFuture(new CancellationException());
                CompletableFuture<ObjectNode> output =
                    CompletableFuture.completedFuture(codec.object());
                for (Plan plan : plans)
                  output =
                      output.thenCompose(
                          out -> {
                            if (scope.stopped())
                              return CompletableFuture.failedFuture(new CancellationException());
                            if (plan.excluded()) {
                              if (plan.prop().loading() == Prop.Loading.DEFERRED
                                  && !loaded(plan.prop(), plan.path()))
                                metadata
                                    .withObject("/deferredProps")
                                    .withArray(plan.prop().group())
                                    .add(plan.path());
                              collect(plan, null);
                              return CompletableFuture.completedFuture(out);
                            }
                            if (plan.prop().source() instanceof Prop.Nested nested) {
                              collect(plan, null);
                              return level(
                                      nested.props(),
                                      plan.path(),
                                      inheritedAlways || plan.prop().always())
                                  .thenApply(
                                      v -> {
                                        insert(out, relative(plan.path(), prefix), v);
                                        return out;
                                      });
                            }
                            return plan.value()
                                .handle(
                                    (result, error) -> {
                                      if (error != null) {
                                        if (!plan.prop().rescued())
                                          throw new PropResolutionException(plan.path(), error);
                                        metadata.withArray("rescuedProps").add(plan.path());
                                      } else {
                                        if (plan.prop().scroll() && result.scroll() == null)
                                          throw new PropResolutionException(
                                              plan.path(),
                                              new IllegalArgumentException(
                                                  "Scroll prop must return ScrollPage"));
                                        collect(plan, result.scroll());
                                        insert(out, relative(plan.path(), prefix), result.json());
                                      }
                                      return out;
                                    });
                          });
                return output;
              });
    }

    CompletableFuture<Value> schedule(String path, Prop.Source source) {
      var result = scope.track(new CompletableFuture<Value>());
      var task =
          new FutureTask<Void>(
              () -> {
                if (scope.stopped() || result.isDone()) return null;
                if (!permits.tryAcquire()) {
                  result.completeExceptionally(
                      new PropResolutionException(
                          path, new RejectedExecutionException("Prop concurrency limit")));
                  return null;
                }
                result.whenComplete((value, error) -> permits.release());
                if (scope.stopped() || result.isDone()) return null;
                try {
                  if (source instanceof Prop.Computed computed) {
                    var computedValue = computed.task().get();
                    if (!scope.stopped() && !result.isDone()) result.complete(value(computedValue));
                  } else {
                    var stage =
                        Objects.requireNonNull(
                            ((Prop.Async) source).task().get(), "Async prop returned null stage");
                    var upstream = scope.track(stage.toCompletableFuture());
                    upstream.whenComplete(
                        (value, error) -> {
                          if (scope.stopped() || result.isDone()) return;
                          if (error != null) result.completeExceptionally(error);
                          else {
                            try {
                              result.complete(PropsResolver.this.value(value));
                            } catch (Throwable failure) {
                              result.completeExceptionally(failure);
                            }
                          }
                        });
                  }
                } catch (Throwable error) {
                  result.completeExceptionally(new PropResolutionException(path, error));
                }
                return null;
              }) {
            @Override
            protected void done() {
              if (isCancelled() && executor instanceof ThreadPoolExecutor pool) pool.remove(this);
            }
          };
      scope.track(task);
      if (scope.stopped() || result.isDone()) return result;
      executor.execute(task);
      // Cancellation may have won immediately before execute enqueued the handle.
      if (task.isCancelled() && executor instanceof ThreadPoolExecutor pool) pool.remove(task);
      return result;
    }

    void collect(Plan plan, ScrollPage scroll) {
      String path = plan.path();
      Prop prop = plan.prop();
      boolean reset = request.list("x-inertia-reset").contains(path);
      Prop.Merge merge = prop.mergeOptions();
      if (merge != null
          && (!plan.excluded() || prop.loading() != Prop.Loading.EAGER)
          && !reset
          && contributes(path)) {
        if (merge.deep()) metadata.withArray("deepMergeProps").add(path);
        else if (scroll != null)
          metadata
              .withArray(
                  "prepend".equals(request.header("x-inertia-infinite-scroll-merge-intent"))
                      ? "prependProps"
                      : "mergeProps")
              .add(path + "." + scroll.wrapper());
        else if (merge.appendAt().isEmpty() && merge.prependAt().isEmpty())
          metadata.withArray(merge.prependRoot() ? "prependProps" : "mergeProps").add(path);
        else {
          merge.appendAt().forEach(at -> metadata.withArray("mergeProps").add(path + "." + at));
          merge.prependAt().forEach(at -> metadata.withArray("prependProps").add(path + "." + at));
        }
        merge.matchOn().forEach(on -> metadata.withArray("matchPropsOn").add(path + "." + on));
      }
      if (scroll != null) {
        var meta = codec.object();
        meta.put("pageName", scroll.pageName());
        meta.set("previousPage", codec.value(scroll.previousPage()));
        meta.set("nextPage", codec.value(scroll.nextPage()));
        meta.set("currentPage", codec.value(scroll.currentPage()));
        meta.put("reset", reset);
        metadata.withObject("/scrollProps").set(path, meta);
      }
      if (prop.onceOptions() != null && contributes(path)) {
        var once = prop.onceOptions();
        var meta = codec.object();
        meta.put("prop", path);
        if (once.ttl() == null) meta.putNull("expiresAt");
        else
          meta.put(
              "expiresAt",
              Math.multiplyExact(
                  Math.addExact(clock.instant().getEpochSecond(), once.ttl().toSeconds()), 1000L));
        metadata.withObject("/onceProps").set(once.key() == null ? path : once.key(), meta);
      }
    }

    JsonNode filter(JsonNode value, String path, boolean always) {
      if (!partial || always || !value.isObject()) return value;
      var result = codec.object();
      value
          .fields()
          .forEachRemaining(
              e -> {
                String child = path + "." + e.getKey();
                if (matches(child)) result.set(e.getKey(), filter(e.getValue(), child, false));
              });
      return result;
    }
  }

  private static String relative(String path, String prefix) {
    return prefix.isEmpty() ? path : path.substring(prefix.length() + 1);
  }

  private static boolean within(String path, String ancestor) {
    return path.equals(ancestor) || path.startsWith(ancestor + ".");
  }

  private static void insert(ObjectNode root, String path, JsonNode value) {
    String[] keys = path.split("\\.");
    var node = root;
    for (int i = 0; i < keys.length - 1; i++)
      node = node.withObject("/" + keys[i].replace("~", "~0").replace("/", "~1"));
    node.set(keys[keys.length - 1], value);
  }
}
