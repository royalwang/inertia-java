package io.inertia.spring;

import java.util.*;
import org.springframework.validation.BindingResult;

/**
 * Copies messages only; never serializes rejected values, target objects, or validator internals.
 *
 * <p>Messages themselves are used verbatim, so validators must produce client-safe text. Bracketed
 * field paths become dotted strings, and global errors use the literal key {@code _form}.
 */
public final class ValidationBridge {
  private ValidationBridge() {}

  private static String dotted(String field) {
    return field.replace("[", ".").replace("]", "");
  }

  /**
   * Copies all BindingResult messages into the core immutable validation representation.
   *
   * @param result binding/validation result whose messages are intended for the client
   * @return immutable field messages retaining result order
   */
  public static io.inertia.core.ValidationErrors errors(BindingResult result) {
    return io.inertia.core.ValidationErrors.from(allErrors(result));
  }

  /**
   * Copies the first message per dotted field and first global message under {@code _form}.
   *
   * @param result binding/validation result
   * @return unmodifiable field map; null messages use Invalid value or Invalid form defaults
   */
  public static Map<String, String> firstErrors(BindingResult result) {
    var errors = new LinkedHashMap<String, String>();
    result
        .getFieldErrors()
        .forEach(
            error ->
                errors.putIfAbsent(
                    dotted(error.getField()),
                    Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")));
    result
        .getGlobalErrors()
        .forEach(
            error ->
                errors.putIfAbsent(
                    "_form",
                    Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid form")));
    return Collections.unmodifiableMap(errors);
  }

  /**
   * Copies all messages, retaining order and duplicates within each field.
   *
   * @param result binding/validation result
   * @return unmodifiable map of immutable lists; global errors append under {@code _form}
   */
  public static Map<String, List<String>> allErrors(BindingResult result) {
    var errors = new LinkedHashMap<String, List<String>>();
    result
        .getFieldErrors()
        .forEach(
            error ->
                errors
                    .computeIfAbsent(dotted(error.getField()), key -> new ArrayList<>())
                    .add(Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")));
    result
        .getGlobalErrors()
        .forEach(
            error ->
                errors
                    .computeIfAbsent("_form", key -> new ArrayList<>())
                    .add(Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid form")));
    errors.replaceAll((key, value) -> List.copyOf(value));
    return Collections.unmodifiableMap(errors);
  }
}
