package io.inertia.spring;

import io.inertia.core.ValidationErrors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.*;

/** Optional Jakarta Validation integration. Only node paths and messages are read. */
public final class JakartaValidationBridge {
  private JakartaValidationBridge() {}

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
