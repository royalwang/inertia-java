package io.inertia.core;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface SsrGateway {
  CompletionStage<Result> render(Page page, InertiaRequest request);

  sealed interface Result permits Rendered, Fallback {}

  record Rendered(String head, String body) implements Result {}

  record Fallback(String reason) implements Result {}
}
