package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Immutable error bags with the Rust adapter's default-bag precedence. */
public final class ErrorBags {
  private final Map<String, ValidationErrors> bags;

  private ErrorBags(Map<String, ValidationErrors> bags) {
    this.bags = Collections.unmodifiableMap(new LinkedHashMap<>(bags));
  }

  /**
   * Creates an empty immutable collection of named validation bags.
   *
   * @return empty bags
   */
  public static ErrorBags empty() {
    return new ErrorBags(Map.of());
  }

  /**
   * Adds a bag or appends messages to the existing bag of the same name.
   *
   * @param bag non-null literal bag name; no non-blank restriction is imposed here
   * @param errors messages to append
   * @return new immutable bags
   * @throws NullPointerException if bag or errors is null
   */
  public ErrorBags with(String bag, ValidationErrors errors) {
    Objects.requireNonNull(bag);
    var combined = new LinkedHashMap<>(bags);
    combined.put(bag, combined.getOrDefault(bag, ValidationErrors.empty()).merge(errors));
    return new ErrorBags(combined);
  }

  /**
   * Merges bags by name, appending messages within matching fields.
   *
   * @param other bags to append
   * @return new immutable bags preserving existing order
   */
  public ErrorBags merge(ErrorBags other) {
    ErrorBags combined = this;
    for (var entry : other.bags.entrySet())
      combined = combined.with(entry.getKey(), entry.getValue());
    return combined;
  }

  static ErrorBags fromJson(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) return empty();
    if (!value.isObject()) throw new IllegalArgumentException("Stored error bags must be objects");
    ErrorBags bags = empty();
    var entries = value.fields();
    while (entries.hasNext()) {
      var entry = entries.next();
      if (!entry.getValue().isObject())
        throw new IllegalArgumentException("Stored error bag must be an object");
      var messages = new LinkedHashMap<String, Object>();
      entry
          .getValue()
          .fields()
          .forEachRemaining(
              field -> {
                if (field.getValue().isTextual())
                  messages.put(field.getKey(), field.getValue().textValue());
                else if (field.getValue().isArray()) {
                  var list = new ArrayList<String>();
                  field
                      .getValue()
                      .forEach(
                          message -> {
                            if (!message.isTextual())
                              throw new IllegalArgumentException(
                                  "Stored validation message must be a string");
                            list.add(message.textValue());
                          });
                  messages.put(field.getKey(), list);
                } else
                  throw new IllegalArgumentException(
                      "Stored validation messages must be strings or arrays");
              });
      bags = bags.with(entry.getKey(), ValidationErrors.from(messages));
    }
    return bags;
  }

  ObjectNode toJson() {
    var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    bags.forEach(
        (bag, errors) -> {
          var fields = value.putObject(bag);
          errors
              .messages()
              .forEach(
                  (field, messages) -> {
                    var array = fields.putArray(field);
                    messages.forEach(array::add);
                  });
        });
    return value;
  }

  /**
   * Builds validation props using default-bag precedence.
   *
   * <p>If a default bag exists, named bags are omitted. Its fields are returned directly unless a
   * requested bag name is supplied, in which case they are wrapped under that name. Without a
   * default bag, all named bags are returned; requestedBag does not filter them.
   *
   * @param requestedBag client-requested bag wrapper, or null for direct default-bag fields
   * @param all true for all messages per field, false for first-message strings
   * @return unmodifiable client error map
   */
  public Map<String, Object> toProp(String requestedBag, boolean all) {
    if (bags.containsKey("default")) {
      var errors = bags.get("default").toProp(all);
      if (requestedBag != null) return Map.of(requestedBag, errors);
      return errors;
    }
    var value = new LinkedHashMap<String, Object>();
    bags.forEach((bag, errors) -> value.put(bag, errors.toProp(all)));
    return Collections.unmodifiableMap(value);
  }
}
