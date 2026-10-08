package io.inertia.core;

import java.util.*;

/** Prepared response: protocol changes happen before any bytes are written. */
public record HttpOutcome(int status, Map<String, List<String>> headers, String body) {
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

  public static HttpOutcome empty(int status) {
    return new HttpOutcome(status, Map.of(), "");
  }

  public HttpOutcome withHeader(String key, String value) {
    var copy = new LinkedHashMap<>(headers);
    copy.put(key.toLowerCase(Locale.ROOT), List.of(value));
    return new HttpOutcome(status, copy, body);
  }

  public HttpOutcome withStatus(int value) {
    return new HttpOutcome(value, headers, body);
  }

  public String header(String key) {
    var values = headers.get(key.toLowerCase(Locale.ROOT));
    return values == null ? null : values.getFirst();
  }

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
