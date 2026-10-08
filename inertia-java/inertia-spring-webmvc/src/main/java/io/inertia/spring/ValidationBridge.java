package io.inertia.spring;

import java.util.*;
import org.springframework.validation.BindingResult;

/**
 * Copies messages only; never serializes rejected values, target objects, or validator internals.
 */
public final class ValidationBridge {
  private ValidationBridge() {}

  private static String dotted(String field) {
    return field.replace("[", ".").replace("]", "");
  }

  public static io.inertia.core.ValidationErrors errors(BindingResult result) {
    return io.inertia.core.ValidationErrors.from(allErrors(result));
  }

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
