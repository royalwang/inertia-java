package io.inertia.core;

import java.util.*;

/**
 * Framework-independent pagination DTO. Nullable previous/next support page and cursor pagination.
 */
public record ScrollPage(
    Object data,
    String pageName,
    Object previousPage,
    Object nextPage,
    Object currentPage,
    String wrapper) {
  public ScrollPage {
    Objects.requireNonNull(pageName);
    Objects.requireNonNull(currentPage);
    Objects.requireNonNull(wrapper);
    if (wrapper.isBlank() || wrapper.contains("."))
      throw new IllegalArgumentException("Scroll wrapper must be one key");
  }

  public ScrollPage(Object data, Object previousPage, Object nextPage, Object currentPage) {
    this(data, "page", previousPage, nextPage, currentPage, "data");
  }
}
