package io.inertia.spring;

import io.inertia.core.ValidationErrors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.*;

/**
 * Optional Jakarta Validation integration that reads only property paths and messages.
 *
 * <p>No root beans, invalid values, or provider internals are serialized. Messages and supported
 * map keys are retained as text, so applications must choose client-safe validation messages and
 * paths.
 */
public final class JakartaValidationBridge {
  private JakartaValidationBridge() {}

  /**
   * Copies violations into deterministic immutable field messages.
   *
   * <p>Sorts by dotted field path and then message text to stabilize a provider's unordered Set.
   * Uses property/parameter names and iterable indexes or supported string/numeric/enum keys;
   * unknown iterable positions use {@code *}. A path without named parts becomes {@code _form}.
   *
   * @param violations non-null violations from an application validator
   * @return immutable messages without reading rejected values
   * @throws IllegalArgumentException if a map path uses an unsupported key type
   */
  public static ValidationErrors errors(Set<? extends ConstraintViolation<?>> violations) {
    record Message(String field, String text) {}
    var messages = new ArrayList<Message>();
    violations.forEach(
        violation ->
            messages.add(new Message(dotted(violation.getPropertyPath()), violation.getMessage())));
    // Validator returns a Set: stabilize both field order and first-message selection.
    messages.sort(Comparator.comparing(Message::field).thenComparing(Message::text));
    var fields = new LinkedHashMap<String, List<String>>();
    messages.forEach(
        message ->
            fields.computeIfAbsent(message.field(), key -> new ArrayList<>()).add(message.text()));
    return ValidationErrors.from(fields);
  }

  private static String dotted(Path path) {
    var parts = new ArrayList<String>();
    for (var node : path) {
      if (node.isInIterable()) {
        if (node.getIndex() != null) parts.add(node.getIndex().toString());
        else if (node.getKey() != null) {
          Object key = node.getKey();
          if (!(key instanceof String || key instanceof Number || key instanceof Enum<?>))
            throw new IllegalArgumentException(
                "Validation map paths require string, numeric or enum keys");
          parts.add(key instanceof Enum<?> constant ? constant.name() : key.toString());
        } else parts.add("*");
      }
      if ((node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.PARAMETER)
          && node.getName() != null) parts.add(node.getName());
    }
    return parts.isEmpty() ? "_form" : String.join(".", parts);
  }
}
