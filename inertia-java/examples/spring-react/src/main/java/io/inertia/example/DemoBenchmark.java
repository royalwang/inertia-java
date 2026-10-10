package io.inertia.example;

import io.inertia.core.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in local capacity fixtures; never enabled by the starter or normal example. */
@Controller
@ConditionalOnProperty(name = "inertia.benchmark-enabled", havingValue = "true")
class DemoBenchmark {
  private final java.util.concurrent.ThreadPoolExecutor executor;
  private final long queueCapacity;

  DemoBenchmark(
      @org.springframework.beans.factory.annotation.Qualifier("inertiaPropsExecutor")
          java.util.concurrent.ExecutorService executor) {
    this.executor = (java.util.concurrent.ThreadPoolExecutor) executor;
    this.queueCapacity =
        this.executor.getQueue().size() + this.executor.getQueue().remainingCapacity();
  }

  @GetMapping("/benchmark/resources")
  @ResponseBody
  Map<String, Long> resources() {
    var heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    var pool = executor;
    return Map.of(
        "heapUsedBytes",
        heap.getUsed(),
        "heapCommittedBytes",
        heap.getCommitted(),
        "heapMaxBytes",
        heap.getMax(),
        "activeThreads",
        (long) pool.getActiveCount(),
        "queuedTasks",
        (long) pool.getQueue().size(),
        "queueCapacity",
        queueCapacity);
  }

  @GetMapping("/benchmark/session")
  @ResponseBody
  Map<String, Boolean> session(HttpServletRequest request) {
    request.getSession(true);
    return Map.of("created", true);
  }

  @GetMapping("/benchmark/page")
  InertiaResponse page(
      InertiaContext context,
      @RequestParam(defaultValue = "64") int bytes,
      @RequestParam(defaultValue = "0") int delayMs) {
    if (bytes < 0 || bytes > 1024 * 1024 || delayMs < 0 || delayMs > 1000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    context.flash("benchmark", "request-owned");
    return new InertiaResponse(
            "Users/Index",
            Props.builder()
                .put("users", List.of(Map.of("id", 1, "name", "Ada")))
                .put("largeId", 9007199254740993L)
                .put(
                    "payload",
                    Prop.lazy(
                        () -> {
                          try {
                            Thread.sleep(delayMs);
                          } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new CancellationException("Benchmark provider interrupted");
                          }
                          return "x".repeat(bytes);
                        }))
                .put("stats", Prop.defer(() -> Map.of("total", 1)).group("dashboard"))
                .build())
        .withHeader("Cache-Control", "private, no-store");
  }
}
