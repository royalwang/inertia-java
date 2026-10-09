package io.inertia.core;

import java.util.Map;

/**
 * Synchronous application-owned HTML shell renderer.
 *
 * <p>Embed Page JSON through {@link PageCodec#htmlJson(Page)} and propagate the request nonce to
 * asset helpers. The supplied head and body contain trusted server renderer output; the shell
 * remains responsible for escaping any additional application data and including its assets.
 */
@FunctionalInterface
public interface RootView {
  /**
   * Produces the complete HTML document for a prepared Page.
   *
   * @param view prepared Page, render fragments, request data, and optional CSP nonce
   * @return complete HTML document
   */
  String render(View view);

  /**
   * Input to the HTML shell for one render.
   *
   * @param page prepared Page payload
   * @param head trusted SSR head fragment, or the renderer's empty fallback fragment
   * @param body trusted SSR body or the client-side root fallback
   * @param ssr whether SSR produced the supplied fragments
   * @param data application root-view data; this record does not defensively copy the map
   * @param nonce validated request CSP nonce, or null to omit it
   */
  record View(
      Page page, String head, String body, boolean ssr, Map<String, Object> data, String nonce) {
    /**
     * Validates the optional CSP nonce.
     *
     * @throws IllegalArgumentException if the nonce contains disallowed characters
     */
    public View {
      nonce = CspNonce.require(nonce);
    }

    /**
     * Creates shell input without a CSP nonce.
     *
     * @param page prepared Page payload
     * @param head trusted head fragment
     * @param body trusted body or client-side root fallback
     * @param ssr whether the fragments came from SSR
     * @param data application root-view data
     */
    public View(Page page, String head, String body, boolean ssr, Map<String, Object> data) {
      this(page, head, body, ssr, data, null);
    }
  }

  /**
   * Creates a minimal HTML shell containing the prepared head and body fragments.
   *
   * <p>No application asset tags are added; supply an application shell for a Vite client.
   *
   * @return minimal shell with UTF-8 and viewport metadata
   */
  static RootView minimal() {
    return view ->
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + view.head()
            + "</head><body>"
            + view.body()
            + "</body></html>";
  }
}
