package io.inertia.core;

import java.util.*;

/**
 * Framework-independent pagination DTO. Nullable previous/next support page and cursor pagination.
 *
 * <p>Data and cursor values are retained without copying. The resolver converts them to JSON and
 * emits pagination metadata; applications remain responsible for access control and cursor
 * validity.
 *
 * @param data JSON-convertible page items
 * @param pageName client pagination query parameter name; non-null
 * @param previousPage previous page/cursor, or null when unavailable
 * @param nextPage next page/cursor, or null when unavailable
 * @param currentPage current page/cursor; non-null
 * @param wrapper single non-blank JSON key for items, without a dot
 */
public record ScrollPage(
    Object data,
    String pageName,
    Object previousPage,
    Object nextPage,
    Object currentPage,
    String wrapper) {
  /**
   * Validates the non-null pagination fields and single-key data wrapper.
   *
   * @throws NullPointerException if pageName, currentPage, or wrapper is null
   * @throws IllegalArgumentException if wrapper is blank or contains a dot
   */
  public ScrollPage {
    Objects.requireNonNull(pageName);
    Objects.requireNonNull(currentPage);
    Objects.requireNonNull(wrapper);
    if (wrapper.isBlank() || wrapper.contains("."))
      throw new IllegalArgumentException("Scroll wrapper must be one key");
  }

  /**
   * Creates pagination data using query name {@code page} and wrapper {@code data}.
   *
   * @param data JSON-convertible page items
   * @param previousPage previous page/cursor, or null
   * @param nextPage next page/cursor, or null
   * @param currentPage non-null current page/cursor
   * @throws NullPointerException if currentPage is null
   */
  public ScrollPage(Object data, Object previousPage, Object nextPage, Object currentPage) {
    this(data, "page", previousPage, nextPage, currentPage, "data");
  }
}
