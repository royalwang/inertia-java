package io.inertia.core;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous boundary between the Java render pipeline and an SSR provider.
 *
 * <p>Implementations own their transport and capacity policy. Return a fallback for expected SSR
 * unavailability; cancellation should release request-owned transport work. This SPI has no close
 * method, and lifecycle management belongs to the concrete implementation and application.
 */
@FunctionalInterface
public interface SsrGateway {
  /**
   * Attempts SSR for the prepared Page without forwarding browser credentials by default.
   *
   * @param page prepared Page JSON and its asset version
   * @param request request metadata for provider policy and observation
   * @return non-null stage resolving to trusted fragments or an explicit fallback
   */
  CompletionStage<Result> render(Page page, InertiaRequest request);

  /** Outcome of an SSR attempt: rendered fragments or a client-side rendering fallback. */
  sealed interface Result permits Rendered, Fallback {}

  /**
   * Successful renderer fragments, to be validated by the concrete gateway before construction.
   *
   * @param head trusted HTML head fragment
   * @param body trusted rendered root HTML
   */
  record Rendered(String head, String body) implements Result {}

  /**
   * Expected inability to render; the Java pipeline may produce its CSR shell.
   *
   * @param reason diagnostic reason chosen by the gateway, without secrets or browser credentials
   */
  record Fallback(String reason) implements Result {}
}
