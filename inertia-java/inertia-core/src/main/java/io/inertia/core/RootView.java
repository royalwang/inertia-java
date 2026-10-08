package io.inertia.core;

import java.util.Map;

@FunctionalInterface
public interface RootView {
  String render(View view);

  record View(Page page, String head, String body, boolean ssr, Map<String, Object> data) {}

  static RootView minimal() {
    return view ->
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + view.head()
            + "</head><body>"
            + view.body()
            + "</body></html>";
  }
}
