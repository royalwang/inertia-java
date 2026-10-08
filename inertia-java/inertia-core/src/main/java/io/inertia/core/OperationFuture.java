package io.inertia.core;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Completion/abort arbitration without holding a lock while completing dependent futures. */
final class OperationFuture<T> extends CompletableFuture<T> {
  private final AtomicBoolean settled = new AtomicBoolean();
  private final CancellationScope scope;
  private final Runnable success;
  private final Consumer<Throwable> failure;

  OperationFuture(CancellationScope scope, Runnable success, Consumer<Throwable> failure) {
    this.scope = scope;
    this.success = success;
    this.failure = failure;
  }

  boolean settled() {
    return settled.get();
  }

  @Override
  public boolean complete(T value) {
    if (!settled.compareAndSet(false, true)) return false;
    try {
      success.run();
      scope.release();
      return super.complete(value);
    } catch (Throwable error) {
      return fail(error, true);
    }
  }

  @Override
  public boolean completeExceptionally(Throwable error) {
    Objects.requireNonNull(error);
    if (!settled.compareAndSet(false, true)) return false;
    return fail(error, true);
  }

  @Override
  public boolean cancel(boolean mayInterrupt) {
    return cancel(new CancellationException("Inertia operation cancelled"), mayInterrupt);
  }

  boolean cancel(Throwable reason, boolean mayInterrupt) {
    if (!settled.compareAndSet(false, true)) return isCancelled();
    return fail(reason, mayInterrupt);
  }

  private boolean fail(Throwable error, boolean interrupt) {
    try {
      failure.accept(error);
    } catch (Throwable cleanup) {
      if (cleanup != error) error.addSuppressed(cleanup);
    }
    scope.cancel(error, interrupt);
    return super.completeExceptionally(error);
  }

  @Override
  public void obtrudeValue(T value) {
    throw new UnsupportedOperationException("Inertia owns operation completion");
  }

  @Override
  public void obtrudeException(Throwable error) {
    throw new UnsupportedOperationException("Inertia owns operation completion");
  }
}
