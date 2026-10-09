package io.inertia.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigInteger;

/**
 * JSON conversion and HTML-safe Page serialization using an isolated Jackson mapper copy.
 *
 * <p>Configure the mapper before construction; later changes to the original do not configure this
 * codec. Returned JSON nodes are mutable. HTML escaping is for embedding JSON in a script element,
 * not for arbitrary HTML attribute or JavaScript expression contexts.
 */
public final class PageCodec {
  private final ObjectMapper mapper;
  private static final BigInteger SAFE = BigInteger.valueOf(9007199254740991L);

  /** Creates a codec from a default Jackson mapper. */
  public PageCodec() {
    this(new ObjectMapper());
  }

  /**
   * Creates a codec with a private copy of the supplied mapper.
   *
   * @param mapper configured mapper to copy
   * @throws NullPointerException if the mapper is null
   */
  public PageCodec(ObjectMapper mapper) {
    this.mapper = mapper.copy();
  }

  /**
   * Creates an empty mutable JSON object.
   *
   * @return new object node
   */
  public ObjectNode object() {
    return mapper.createObjectNode();
  }

  /**
   * Converts a Java value to a JSON tree through the configured mapper.
   *
   * @param value value to convert; null becomes JSON null
   * @return converted JSON node
   * @throws IllegalArgumentException if conversion fails
   */
  public JsonNode value(Object value) {
    return mapper.valueToTree(value);
  }

  /**
   * Parses JSON using the configured mapper.
   *
   * @param json JSON text
   * @return parsed tree using Jackson's empty-input and JSON-null semantics
   * @throws IllegalArgumentException if the JSON is invalid
   */
  public JsonNode read(String json) {
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  /**
   * Serializes a Page's defensive data snapshot.
   *
   * @param page Page to serialize
   * @return JSON text
   * @throws IllegalArgumentException if serialization fails
   */
  public String json(Page page) {
    return json(page.data());
  }

  /**
   * Serializes a JSON tree without HTML escaping.
   *
   * @param value JSON tree
   * @return JSON text
   * @throws IllegalArgumentException if serialization fails
   */
  public String json(JsonNode value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize Page", e);
    }
  }

  /**
   * Serializes Page JSON for embedding inside an HTML script element.
   *
   * <p>Escapes slashes, angle brackets, ampersands, and Unicode line separators. Use this instead
   * of raw {@link #json(Page)} when building a root view's Page script.
   *
   * @param page Page to serialize
   * @return JSON text safe for the script element's text content
   * @throws IllegalArgumentException if serialization fails
   */
  public String htmlJson(Page page) {
    return json(page)
        .replace("/", "\\/")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029");
  }

  /**
   * Recursively replaces integers outside JavaScript's safe range with {@code $bigint} objects.
   *
   * <p>The inclusive safe range is minus to plus 9007199254740991. This preserves integer precision
   * only when the client understands the tagged representation; it does not change the input tree.
   *
   * @param input non-null JSON tree
   * @return independent transformed tree
   * @throws NullPointerException if input is null
   */
  public JsonNode bigIntegers(JsonNode input) {
    if (input.isIntegralNumber() && input.bigIntegerValue().abs().compareTo(SAFE) > 0)
      return object().put("$bigint", input.bigIntegerValue().toString());
    if (input.isObject()) {
      var out = object();
      input.fields().forEachRemaining(e -> out.set(e.getKey(), bigIntegers(e.getValue())));
      return out;
    }
    if (input.isArray()) {
      var out = mapper.createArrayNode();
      input.forEach(n -> out.add(bigIntegers(n)));
      return out;
    }
    return input.deepCopy();
  }
}
