package io.inertia.core;

import java.util.*;

/** Immutable validation messages; field names are dotted strings, not nested prop paths. */
public final class ValidationErrors {
  private final Map<String, List<String>> messages;

  private ValidationErrors(Map<String, List<String>> messages) {
    var copy = new LinkedHashMap<String, List<String>>();
    messages.forEach(
        (field, values) -> copy.put(Objects.requireNonNull(field), List.copyOf(values)));
    this.messages = Collections.unmodifiableMap(copy);
  }

  public static ValidationErrors empty() {
    return new ValidationErrors(Map.of());
  }

  /** Accepts either one message or a list of messages per field. Other values are rejected. */
  public static ValidationErrors from(Map<String, ?> fields) {
    var messages = new LinkedHashMap<String, List<String>>();
    fields.forEach(
        (field, value) -> {
          if (value instanceof String message) messages.put(field, List.of(message));
          else if (value instanceof List<?> list) {
            var strings = new ArrayList<String>();
            for (Object element : list) {
              if (!(element instanceof String message))
                throw new IllegalArgumentException("Validation messages must be strings");
              strings.add(message);
            }
            messages.put(field, strings);
          } else
            throw new IllegalArgumentException(
                "Validation messages must be strings or lists of strings");
        });
    return new ValidationErrors(messages);
  }

  public ValidationErrors with(String field, String message) {
    return merge(from(Map.of(field, message)));
  }

  public ValidationErrors merge(ValidationErrors other) {
    var combined = new LinkedHashMap<>(messages);
    other.messages.forEach(
        (field, values) -> {
          var joined = new ArrayList<>(combined.getOrDefault(field, List.of()));
          joined.addAll(values);
          combined.put(field, List.copyOf(joined));
        });
    return new ValidationErrors(combined);
  }

  public Map<String, List<String>> messages() {
    return messages;
  }

  public Map<String, Object> toProp(boolean all) {
    var fields = new LinkedHashMap<String, Object>();
    messages.forEach(
        (field, values) ->
            fields.put(field, all ? values : values.isEmpty() ? "" : values.getFirst()));
    return Collections.unmodifiableMap(fields);
  }
}
