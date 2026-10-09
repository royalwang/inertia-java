package io.inertia.core;

import java.util.*;

/**
 * Immutable prepared HTTP response; protocol changes happen before any bytes are written.
 *
 * <p>Header names are lowercased and lists copied. Header validation prevents CR/LF injection but
 * does not authorize redirect destinations. The adapter owns writing the resulting status, headers,
 * and body to the transport.
 *
 * @param status HTTP status from 100 through 599
 * @param headers header names and lists of values; copied at construction
 * @param body non-null response text
 */
public record HttpOutcome(int status, Map<String, List<String>> headers, String body) {
  /**
   * Validates status/header syntax and copies header collections.
   *
   * @throws IllegalArgumentException if status, header names, or header values are invalid
   * @throws NullPointerException if a required field, name, list, or value is null
   */
  public HttpOutcome {
    if (status < 100 || status > 599) throw new IllegalArgumentException("Invalid status");
    var copy = new LinkedHashMap<String, List<String>>();
    headers.forEach(
        (key, values) -> {
          if (!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))
            throw new IllegalArgumentException("Invalid header name");
          for (String value : values)
            if (value.contains("\r") || value.contains("\n"))
              throw new IllegalArgumentException("Invalid header value");
          copy.put(key.toLowerCase(Locale.ROOT), List.copyOf(values));
        });
    headers = Collections.unmodifiableMap(copy);
    Objects.requireNonNull(body);
  }

  /**
   * Creates an empty-body response without headers.
   *
   * @param status HTTP status from 100 through 599
   * @return empty prepared response
   * @throws IllegalArgumentException if status is invalid
   */
  public static HttpOutcome empty(int status) {
    return new HttpOutcome(status, Map.of(), "");
  }

  /**
   * Copies this response, replacing the values for one case-insensitive header name.
   *
   * @param key header name
   * @param value single replacement value
   * @return new validated response
   * @throws IllegalArgumentException if the name or value violates header syntax
   */
  public HttpOutcome withHeader(String key, String value) {
    var copy = new LinkedHashMap<>(headers);
    copy.put(key.toLowerCase(Locale.ROOT), List.of(value));
    return new HttpOutcome(status, copy, body);
  }

  /**
   * Copies this response with a new status.
   *
   * @param value HTTP status from 100 through 599
   * @return new validated response
   * @throws IllegalArgumentException if the status is invalid
   */
  public HttpOutcome withStatus(int value) {
    return new HttpOutcome(value, headers, body);
  }

  /**
   * Returns the first value for a case-insensitive header name.
   *
   * @param key header name
   * @return first value or null when no such header exists
   * @throws NoSuchElementException if an existing header has an empty value list
   */
  public String header(String key) {
    var values = headers.get(key.toLowerCase(Locale.ROOT));
    return values == null ? null : values.getFirst();
  }

  /**
   * Ensures caches vary on {@code X-Inertia}, preserving existing Vary values.
   *
   * <p>An existing case-insensitive X-Inertia token or wildcard already satisfies the requirement.
   *
   * @return response with an appropriate Vary header
   */
  public HttpOutcome vary() {
    var values = new ArrayList<>(headers.getOrDefault("vary", List.of()));
    boolean present =
        values.stream()
            .flatMap(v -> Arrays.stream(v.split(",")))
            .anyMatch(v -> v.trim().equalsIgnoreCase("x-inertia") || v.trim().equals("*"));
    if (!present) values.add("X-Inertia");
    var copy = new LinkedHashMap<>(headers);
    copy.put("vary", values);
    return new HttpOutcome(status, copy, body);
  }
}
