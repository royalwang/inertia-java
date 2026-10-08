package io.inertia.core;

import java.util.*;
import java.util.concurrent.*;

/** Request-owned handles. Never invokes application cancellation while holding the scope lock. */
final class CancellationScope {
  private final List<Future<?>> tasks = new ArrayList<>();
  private boolean stopped;
  private Throwable failure;
  private boolean interrupt = true;

  <T extends Future<?>> T track(T task) {
    boolean cancel;
    Throwable reason;
    boolean mayInterrupt;
    synchronized (this) {
      cancel = stopped;
      reason = failure;
      mayInterrupt = interrupt;
      if (!cancel) tasks.add(task);
    }
    if (cancel) cancel(task, reason, mayInterrupt);
    return task;
  }

  synchronized boolean stopped() {
    return stopped;
  }

  synchronized void release() {
    stopped = true;
    tasks.clear();
  }

  void cancel(Throwable reason, boolean mayInterrupt) {
    List<Future<?>> pending;
    synchronized (this) {
      if (stopped) return;
      stopped = true;
      failure = reason;
      interrupt = mayInterrupt;
      pending = List.copyOf(tasks);
      tasks.clear();
    }
    for (var task : pending) cancel(task, reason, mayInterrupt);
  }

  private static void cancel(Future<?> task, Throwable reason, boolean interrupt) {
    try {
      if (task instanceof OperationFuture<?> operation && reason != null)
        operation.cancel(reason, interrupt);
      else task.cancel(interrupt);
    } catch (Throwable cleanup) {
      if (reason != null && reason != cleanup) reason.addSuppressed(cleanup);
    }
  }
}
