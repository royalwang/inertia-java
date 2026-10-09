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

  /**
   * Creates an empty immutable set of field messages.
   *
   * @return empty validation errors
   */
  public static ValidationErrors empty() {
    return new ValidationErrors(Map.of());
  }

  /**
   * Copies field messages from strings or lists of strings.
   *
   * <p>Field names remain literal strings, including dots; they are not nested prop paths. Empty
   * lists are retained. Other value types, null messages, and null field names are rejected.
   *
   * @param fields mapping of field names to one message or a List of messages
   * @return immutable messages preserving field and message order
   * @throws IllegalArgumentException if a value or list element is not a string
   * @throws NullPointerException if the map or a field name is null
   */
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

  /**
   * Appends one message to a field without changing this object.
   *
   * @param field literal field name
   * @param message message to append
   * @return combined immutable errors
   * @throws NullPointerException if field or message is null
   */
  public ValidationErrors with(String field, String message) {
    return merge(from(Map.of(field, message)));
  }

  /**
   * Appends another error set's messages by field, retaining duplicates and order.
   *
   * @param other immutable errors to append
   * @return combined immutable errors
   */
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

  /**
   * Returns immutable field-to-message lists.
   *
   * @return immutable ordered message map
   */
  public Map<String, List<String>> messages() {
    return messages;
  }

  /**
   * Builds the client-facing validation representation.
   *
   * @param all true for message lists, false for the first message or an empty string per field
   * @return unmodifiable field map; dotted field names remain literal
   */
  public Map<String, Object> toProp(boolean all) {
    var fields = new LinkedHashMap<String, Object>();
    messages.forEach(
        (field, values) ->
            fields.put(field, all ? values : values.isEmpty() ? "" : values.getFirst()));
    return Collections.unmodifiableMap(fields);
  }
}
